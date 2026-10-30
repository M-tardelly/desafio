package com.example.activation.application;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(5, Duration.ofSeconds(2), Duration.ofMinutes(1));

    @Test
    void limitsNumberOfAttempts() {
        assertThat(policy.hasAttemptsLeft(4)).isTrue();
        assertThat(policy.hasAttemptsLeft(5)).isFalse();
    }

    @RepeatedTest(20)
    void growsExponentiallyWithJitter() {
        assertThat(policy.delayBeforeNextAttempt(1, null)).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(2));
        assertThat(policy.delayBeforeNextAttempt(2, null)).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(4));
        assertThat(policy.delayBeforeNextAttempt(3, null)).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(8));
    }

    @RepeatedTest(20)
    void neverExceedsMaxBackoff() {
        assertThat(policy.delayBeforeNextAttempt(30, null))
                .isBetween(Duration.ofSeconds(30), Duration.ofMinutes(1));
    }

    @Test
    void honorsRetryAfterUpToMaxBackoff() {
        assertThat(policy.delayBeforeNextAttempt(1, Duration.ofSeconds(30))).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.delayBeforeNextAttempt(1, Duration.ofHours(1))).isEqualTo(Duration.ofMinutes(1));
    }
}
