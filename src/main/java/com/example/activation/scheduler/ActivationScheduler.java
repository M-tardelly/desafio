package com.example.activation.scheduler;

import com.example.activation.service.ActivationProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.processing", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ActivationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ActivationScheduler.class);

    private final ActivationProcessingService processingService;

    public ActivationScheduler(ActivationProcessingService processingService) {
        this.processingService = processingService;
    }

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
