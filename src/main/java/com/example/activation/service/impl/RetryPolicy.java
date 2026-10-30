package com.example.activation.service.impl;

import com.example.activation.config.ProcessingProperties;
import com.example.activation.model.ActivationRequest;
import com.example.activation.utils.BackoffUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Decide o que fazer após uma falha temporária: nova tentativa com backoff ou, ao atingir o limite, FAILED.
 */
@Component
@RequiredArgsConstructor
class RetryPolicy {

    private final ProcessingProperties properties;
    private final Clock clock;

    /**
     * @param retryAfter tempo pedido pelo provedor; quando presente, substitui o backoff calculado.
     */
    void onTemporaryFailure(ActivationRequest request, String reason, Duration retryAfter) {
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
