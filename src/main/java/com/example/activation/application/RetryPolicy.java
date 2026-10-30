package com.example.activation.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Backoff exponencial com jitter ("equal jitter": metade fixa, metade aleatória) para evitar que
 * várias solicitações que falharam juntas voltem ao provedor no mesmo instante.
 */
@Component
public class RetryPolicy {

    private static final int MAX_EXPONENT = 20;

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;

    @Autowired
    public RetryPolicy(ProcessingProperties properties) {
        this(properties.maxAttempts(), properties.initialBackoff(), properties.maxBackoff());
    }

    RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
    }

    public boolean hasAttemptsLeft(int attemptsSoFar) {
        return attemptsSoFar < maxAttempts;
    }

    public Duration delayBeforeNextAttempt(int attemptsSoFar, Duration retryAfter) {
        if (retryAfter != null && !retryAfter.isNegative()) {
            return retryAfter.compareTo(maxBackoff) > 0 ? maxBackoff : retryAfter;
        }
        int exponent = Math.min(Math.max(attemptsSoFar - 1, 0), MAX_EXPONENT);
        long exponentialMillis = initialBackoff.toMillis() * (1L << exponent);
        long cappedMillis = Math.min(exponentialMillis, maxBackoff.toMillis());
        long fixedPart = cappedMillis / 2;
        long jitter = ThreadLocalRandom.current().nextLong(cappedMillis - fixedPart + 1);
        return Duration.ofMillis(fixedPart + jitter);
    }
}
