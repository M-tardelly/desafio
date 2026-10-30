package com.example.activation.service.impl;

import com.example.activation.config.ProcessingProperties;
import com.example.activation.model.ActivationRequest;
import com.example.activation.model.ActivationStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisão entre nova tentativa e falha definitiva após uma falha temporária.
 *
 * @since 2026-10-29
 */
class RetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-26T14:03:11Z");
    private static final int MAX_ATTEMPTS = 3;

    private final ProcessingProperties properties = new ProcessingProperties(false, 50, MAX_ATTEMPTS,
            Duration.ofSeconds(2), Duration.ofMinutes(5), Duration.ofMinutes(2));
    private final RetryPolicy retryPolicy = new RetryPolicy(properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void schedulesRetryWithBackoffWhileAttemptsRemain() {
        ActivationRequest request = processingRequestWithAttempts(1);

        retryPolicy.onTemporaryFailure(request, "HTTP 500", null);

        assertThat(request.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(request.getLastError()).isEqualTo("HTTP 500");
        assertThat(request.getNextAttemptAt()).isBetween(NOW.plusSeconds(1), NOW.plusSeconds(2));
    }

    @Test
    void usesRetryAfterWhenProviderSendsIt() {
        ActivationRequest request = processingRequestWithAttempts(1);

        retryPolicy.onTemporaryFailure(request, "HTTP 503", Duration.ofSeconds(30));

        assertThat(request.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void failsWhenAttemptLimitIsReached() {
        ActivationRequest request = processingRequestWithAttempts(MAX_ATTEMPTS);

        retryPolicy.onTemporaryFailure(request, "HTTP 500", null);

        assertThat(request.getStatus()).isEqualTo(ActivationStatus.FAILED);
        assertThat(request.getLastError()).isEqualTo("Limite de 3 tentativas atingido. Último erro: HTTP 500");
    }

    private static ActivationRequest processingRequestWithAttempts(int attempts) {
        ActivationRequest request = ActivationRequest.create("system-a", "EXT-1", "CUST-1", "INTERNET-500", "corr", NOW);
        request.startProcessing(NOW);
        for (int attempt = 2; attempt <= attempts; attempt++) {
            request.scheduleRetry(NOW, "retry", NOW);
            request.startProcessing(NOW);
        }
        return request;
    }
}
