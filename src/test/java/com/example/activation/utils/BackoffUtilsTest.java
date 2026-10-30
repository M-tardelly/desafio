package com.example.activation.utils;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffUtilsTest {

    private static final Duration INITIAL = Duration.ofSeconds(2);
    private static final Duration MAX = Duration.ofMinutes(1);

    @RepeatedTest(20)
    void growsExponentiallyWithJitter() {
        assertThat(BackoffUtils.nextDelay(1, INITIAL, MAX, null))
                .isBetween(Duration.ofSeconds(1), Duration.ofSeconds(2));
        assertThat(BackoffUtils.nextDelay(2, INITIAL, MAX, null))
                .isBetween(Duration.ofSeconds(2), Duration.ofSeconds(4));
        assertThat(BackoffUtils.nextDelay(3, INITIAL, MAX, null))
                .isBetween(Duration.ofSeconds(4), Duration.ofSeconds(8));
    }

    @RepeatedTest(20)
    void neverExceedsMaxBackoff() {
        assertThat(BackoffUtils.nextDelay(30, INITIAL, MAX, null))
                .isBetween(Duration.ofSeconds(30), Duration.ofMinutes(1));
    }

    @Test
    void honorsRetryAfterUpToMaxBackoff() {
        assertThat(BackoffUtils.nextDelay(1, INITIAL, MAX, Duration.ofSeconds(30))).isEqualTo(Duration.ofSeconds(30));
        assertThat(BackoffUtils.nextDelay(1, INITIAL, MAX, Duration.ofHours(1))).isEqualTo(MAX);
    }
}
