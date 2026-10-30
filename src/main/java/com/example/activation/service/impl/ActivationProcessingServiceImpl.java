package com.example.activation.service.impl;

import com.example.activation.config.ProcessingProperties;
import com.example.activation.dto.ProviderResult;
import com.example.activation.exception.InvalidStatusTransitionException;
import com.example.activation.model.ActivationRequest;
import com.example.activation.model.ActivationStatus;
import com.example.activation.repository.ActivationRequestRepository;
import com.example.activation.service.ActivationProcessingService;
import com.example.activation.service.ProviderService;
import com.example.activation.utils.BackoffUtils;
import com.example.activation.utils.CorrelationIdUtils;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
@Service
public class ActivationProcessingServiceImpl implements ActivationProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ActivationProcessingServiceImpl.class);

    private final ActivationRequestRepository repository;
    private final ProviderService providerService;
    private final TransactionTemplate transactionTemplate;
    private final ProcessingProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public ActivationProcessingServiceImpl(ActivationRequestRepository repository, ProviderService providerService,
                                           TransactionTemplate transactionTemplate, ProcessingProperties properties,
                                           Clock clock, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.providerService = providerService;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public int processDueRequests() {
        var dueIds = repository.findIdsReadyForProcessing(now(), Limit.of(properties.batchSize()));
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
        var threshold = now().minus(properties.processingLease());
        var stuckIds = repository.findIdsStuckInProcessing(threshold, Limit.of(properties.batchSize()));
        int recovered = 0;
        for (UUID id : stuckIds) {
            try {
                if (Boolean.TRUE.equals(transactionTemplate.execute(tx -> recover(id, threshold)))) {
                    recovered++;
                }
            } catch (OptimisticLockingFailureException e) {
                log.debug("Request {} changed while being recovered, skipping", id);
            }
        }
        return recovered;
    }

    private boolean recover(UUID id, Instant threshold) {
        var request = repository.findById(id).orElse(null);
        if (request == null || request.getStatus() != ActivationStatus.PROCESSING
                || !request.getUpdatedAt().isBefore(threshold)) {
            return false;
        }
        log.warn("Request {} stuck in PROCESSING since {}, rescheduling", id, request.getUpdatedAt());
        handleTemporaryFailure(request, "Processamento interrompido antes da resposta do provedor", null);
        return true;
    }

    private boolean process(UUID id) {
        var claimed = claim(id);
        if (claimed.isEmpty()) {
            return false;
        }
        var request = claimed.get();
        try (var ignored = CorrelationIdUtils.bind(request.getCorrelationId())) {
            var result = callProvider(request);
            applyResult(id, result);
        }
        return true;
    }

    private Optional<ActivationRequest> claim(UUID id) {
        try {
            return Optional.ofNullable(transactionTemplate.execute(tx -> {
                var request = repository.findById(id).orElse(null);
                if (request == null || !request.isReadyForProcessing(now())) {
                    return null;
                }
                request.startProcessing(now());
                return repository.saveAndFlush(request);
            }));
        } catch (OptimisticLockingFailureException e) {
            log.debug("Request {} already claimed by another worker", id);
            return Optional.empty();
        }
    }

    private ProviderResult callProvider(ActivationRequest request) {
        try {
            return providerService.activate(request);
        } catch (RuntimeException e) {
            log.error("Unexpected error calling provider for request {}", request.getId(), e);
            return new ProviderResult.TemporaryFailure("Erro inesperado na integração com o provedor", null);
        }
    }

    private void applyResult(UUID id, ProviderResult result) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                var request = repository.findById(id).orElseThrow();
                switch (result) {
                    case ProviderResult.Activated activated -> request.complete(now());
                    case ProviderResult.Rejected rejected -> request.fail(rejected.reason(), now());
                    case ProviderResult.TemporaryFailure failure ->
                            handleTemporaryFailure(request, failure.reason(), failure.retryAfter());
                }
                log.info("Request {} is now {} after attempt {}", id, request.getStatus(), request.getAttempts());
                meterRegistry.counter("activation.requests.attempts", "result", request.getStatus().name())
                        .increment();
            });
        } catch (OptimisticLockingFailureException | InvalidStatusTransitionException e) {
            // A solicitação foi reagendada por recoverStuckRequests enquanto a chamada estava em andamento.
            // O próximo envio usa a mesma chave de idempotência, então o provedor não ativa duas vezes.
            log.warn("Discarding provider result for request {}: {}", id, e.getMessage());
        }
    }

    private void handleTemporaryFailure(ActivationRequest request, String reason, Duration retryAfter) {
        if (request.getAttempts() >= properties.maxAttempts()) {
            request.fail("Limite de %d tentativas atingido. Último erro: %s"
                    .formatted(request.getAttempts(), reason), now());
            return;
        }
        var delay = BackoffUtils.nextDelay(request.getAttempts(), properties.initialBackoff(),
                properties.maxBackoff(), retryAfter);
        request.scheduleRetry(now().plus(delay), reason, now());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
