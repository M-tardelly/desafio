package com.example.activation.scheduler;

import com.example.activation.service.ActivationProcessingService;
import com.example.activation.utils.LogBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara periodicamente o processamento assíncrono: reagenda solicitações presas em PROCESSING e envia
 * as pendentes ao provedor. Pode ser desligado por instância com {@code app.processing.scheduler-enabled=false}.
 *
 * @since 2026-10-29
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.processing", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ActivationScheduler {

    private final ActivationProcessingService processingService;

    /**
     * Um ciclo de processamento. Erros são registrados e o próximo ciclo roda normalmente.
     */
    @Scheduled(fixedDelayString = "${app.processing.poll-interval:PT1S}")
    public void run() {
        try {
            int recovered = processingService.recoverStuckRequests();
            int processed = processingService.processDueRequests();
            if (recovered > 0 || processed > 0) {
                LogBuilder.debug(log, "Processing cycle finished")
                        .with("recovered", recovered)
                        .with("processed", processed)
                        .log();
            }
        } catch (RuntimeException e) {
            LogBuilder.error(log, "Processing cycle failed")
                    .exception(e)
                    .log();
        }
    }
}
