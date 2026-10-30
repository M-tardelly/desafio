package com.example.activation.service.impl;

import com.example.activation.config.ProcessingProperties;
import com.example.activation.dto.provider.ProviderActivationResult;
import com.example.activation.exception.InvalidStatusTransitionException;
import com.example.activation.model.ActivationRequest;
import com.example.activation.model.ActivationStatus;
import com.example.activation.repository.ActivationRequestRepository;
import com.example.activation.service.ActivationProcessingService;
import com.example.activation.service.ProviderService;
import com.example.activation.utils.BackoffUtils;
import com.example.activation.utils.CorrelationIdUtils;
import com.example.activation.utils.LogBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A própria tabela de solicitações funciona como outbox. Cada solicitação passa por três passos,
 * e a chamada HTTP nunca acontece com transação aberta:
 * <ol>
 *     <li>transação curta que muda PENDING -> PROCESSING (o {@code @Version} garante que só uma
 *     instância "reivindica" a solicitação);</li>
 *     <li>chamada ao provedor, fora de transação;</li>
 *     <li>transação curta que grava o resultado.</li>
 * </ol>
 * Se a aplicação cair entre os passos, a solicitação continua PENDING ou fica presa em PROCESSING
 * e é reagendada por {@link #recoverStuckRequests()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivationProcessingServiceImpl implements ActivationProcessingService {

    private final ActivationRequestRepository repository;
    private final ProviderService providerService;
    private final TransactionTemplate transactionTemplate;
    private final ProcessingProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @Override
    public int processDueRequests() {
        List<UUID> dueIds = repository.findIdsReadyForProcessing(clock.instant(), Limit.of(properties.batchSize()));
        int processed = 0;
        for (UUID id : dueIds) {
            if (process(id)) {
                processed++;
            }
        }
        return processed;
    }

    @Override
    public int recoverStuckRequests() {
        Instant threshold = clock.instant().minus(properties.processingLease());
        List<UUID> stuckIds = repository.findIdsStuckInProcessing(threshold, Limit.of(properties.batchSize()));
        int recovered = 0;
        for (UUID id : stuckIds) {
            try {
                if (Boolean.TRUE.equals(transactionTemplate.execute(tx -> recover(id, threshold)))) {
                    recovered++;
                }
            } catch (OptimisticLockingFailureException e) {
                LogBuilder.debug(log, "Request changed while being recovered, skipping")
                        .with("requestId", id)
                        .log();
            }
        }
        return recovered;
    }

    private boolean recover(UUID id, Instant threshold) {
        ActivationRequest request = repository.findById(id).orElse(null);
        if (request == null || request.getStatus() != ActivationStatus.PROCESSING
                || !request.getUpdatedAt().isBefore(threshold)) {
            return false;
        }
        LogBuilder.warn(log, "Request stuck in PROCESSING, rescheduling")
                .with("requestId", id)
                .with("processingSince", request.getUpdatedAt())
                .with("attempts", request.getAttempts())
                .log();
        handleTemporaryFailure(request, "Processamento interrompido antes da resposta do provedor", null);
        return true;
    }

    private boolean process(UUID id) {
        Optional<ActivationRequest> claimed = claim(id);
        if (claimed.isEmpty()) {
            return false;
        }
        ActivationRequest request = claimed.get();
        try (MDC.MDCCloseable ignored = CorrelationIdUtils.bind(request.getCorrelationId())) {
            ProviderActivationResult result = callProvider(request);
            applyResult(id, result);
        }
        return true;
    }

    private Optional<ActivationRequest> claim(UUID id) {
        try {
            return Optional.ofNullable(transactionTemplate.execute(tx -> {
                ActivationRequest request = repository.findById(id).orElse(null);
                if (request == null || !request.isReadyForProcessing(clock.instant())) {
                    return null;
                }
                request.startProcessing(clock.instant());
                return repository.saveAndFlush(request);
            }));
        } catch (OptimisticLockingFailureException e) {
            LogBuilder.debug(log, "Request already claimed by another worker")
                    .with("requestId", id)
                    .log();
            return Optional.empty();
        }
    }

    private ProviderActivationResult callProvider(ActivationRequest request) {
        try {
            return providerService.activate(request);
        } catch (RuntimeException e) {
            LogBuilder.error(log, "Unexpected error calling provider")
                    .with("requestId", request.getId())
                    .exception(e)
                    .log();
            return new ProviderActivationResult.TemporaryFailure("Erro inesperado na integração com o provedor", null);
        }
    }

    private void applyResult(UUID id, ProviderActivationResult result) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                ActivationRequest request = repository.findById(id).orElseThrow();
                switch (result) {
                    case ProviderActivationResult.Activated activated -> request.complete(clock.instant());
                    case ProviderActivationResult.Rejected rejected -> request.fail(rejected.reason(), clock.instant());
                    case ProviderActivationResult.TemporaryFailure failure ->
                            handleTemporaryFailure(request, failure.reason(), failure.retryAfter());
                }
                LogBuilder.info(log, "Provider result applied")
                        .with("requestId", id)
                        .with("status", request.getStatus())
                        .with("attempts", request.getAttempts())
                        .with("lastError", request.getLastError())
                        .log();
                meterRegistry.counter("activation.requests.attempts", "result", request.getStatus().name())
                        .increment();
            });
        } catch (OptimisticLockingFailureException | InvalidStatusTransitionException e) {
            // A solicitação foi reagendada por recoverStuckRequests enquanto a chamada estava em andamento.
            // O próximo envio usa a mesma chave de idempotência, então o provedor não ativa duas vezes.
            LogBuilder.warn(log, "Discarding provider result, request changed concurrently")
                    .with("requestId", id)
                    .with("reason", e.getMessage())
                    .log();
        }
    }

    private void handleTemporaryFailure(ActivationRequest request, String reason, Duration retryAfter) {
        Instant now = clock.instant();
        if (request.getAttempts() >= properties.maxAttempts()) {
            request.fail("Limite de %d tentativas atingido. Último erro: %s"
                    .formatted(request.getAttempts(), reason), now);
            return;
        }
        Duration delay = BackoffUtils.nextDelay(request.getAttempts(), properties.initialBackoff(),
                properties.maxBackoff(), retryAfter);
        request.scheduleRetry(now.plus(delay), reason, now);
    }
}
