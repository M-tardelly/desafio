package com.example.activation.service.impl;

import com.example.activation.config.ProcessingProperties;
import com.example.activation.dto.provider.ProviderActivationResult;
import com.example.activation.dto.provider.ProviderActivationResult.Activated;
import com.example.activation.dto.provider.ProviderActivationResult.Rejected;
import com.example.activation.dto.provider.ProviderActivationResult.TemporaryFailure;
import com.example.activation.exception.InvalidStatusTransitionException;
import com.example.activation.model.ActivationRequest;
import com.example.activation.model.ActivationStatus;
import com.example.activation.repository.ActivationRequestRepository;
import com.example.activation.service.ActivationProcessingService;
import com.example.activation.service.ProviderService;
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
 * <p>
 * As transações são programáticas ({@link TransactionTemplate}) porque {@code @Transactional} não se aplica
 * a chamadas entre métodos da mesma classe.
 *
 * @since 2026-10-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivationProcessingServiceImpl implements ActivationProcessingService {

    private final ActivationRequestRepository repository;
    private final ProviderService providerService;
    private final RetryPolicy retryPolicy;
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
            if (recover(id, threshold)) {
                recovered++;
            }
        }
        return recovered;
    }

    private boolean process(UUID id) {
        Optional<ActivationRequest> claimed = claim(id);
        if (claimed.isEmpty()) {
            return false;
        }
        ActivationRequest request = claimed.get();
        try (MDC.MDCCloseable ignored = CorrelationIdUtils.bind(request.getCorrelationId())) {
            ProviderActivationResult result = callProvider(request);
            saveResult(id, result);
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
            return new TemporaryFailure("Erro inesperado na integração com o provedor", null);
        }
    }

    private void saveResult(UUID id, ProviderActivationResult result) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                ActivationRequest request = repository.findById(id).orElseThrow();
                applyResult(request, result);
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

    private void applyResult(ActivationRequest request, ProviderActivationResult result) {
        switch (result) {
            case Activated activated -> request.complete(clock.instant());
            case Rejected rejected -> request.fail(rejected.reason(), clock.instant());
            case TemporaryFailure failure ->
                    retryPolicy.onTemporaryFailure(request, failure.reason(), failure.retryAfter());
        }
    }

    private boolean recover(UUID id, Instant threshold) {
        try {
            return Boolean.TRUE.equals(transactionTemplate.execute(tx -> {
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
                retryPolicy.onTemporaryFailure(request,
                        "Processamento interrompido antes da resposta do provedor", null);
                return true;
            }));
        } catch (OptimisticLockingFailureException e) {
            LogBuilder.debug(log, "Request changed while being recovered, skipping")
                    .with("requestId", id)
                    .log();
            return false;
        }
    }
}
