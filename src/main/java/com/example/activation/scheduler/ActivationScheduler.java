package com.example.activation.scheduler;

import com.example.activation.service.ActivationProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.processing", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ActivationScheduler {

    private final ActivationProcessingService processingService;

    @Scheduled(fixedDelayString = "${app.processing.poll-interval:PT1S}")
    public void run() {
        try {
            int recovered = processingService.recoverStuckRequests();
            int processed = processingService.processDueRequests();
            if (recovered > 0 || processed > 0) {
                log.debug("Processing cycle finished: recovered={} processed={}", recovered, processed);
            }
        } catch (RuntimeException e) {
            log.error("Processing cycle failed", e);
        }
    }
}
