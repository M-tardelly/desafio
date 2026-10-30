package com.example.activation.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interpretação do header HTTP Retry-After.
 *
 * @since 2026-10-29
 */
class RetryAfterUtilsTest {

    private static final Instant NOW = Instant.parse("2026-10-21T07:27:00Z");

    @Test
    void parsesSeconds() {
        assertThat(RetryAfterUtils.parse(" 120 ", NOW)).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void parsesHttpDate() {
        assertThat(RetryAfterUtils.parse("Wed, 21 Oct 2026 07:28:00 GMT", NOW)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void pastDateMeansRetryNow() {
        assertThat(RetryAfterUtils.parse("Wed, 21 Oct 2026 07:00:00 GMT", NOW)).isEqualTo(Duration.ZERO);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"soon", "Wed 21 Oct"})
    void returnsNullWhenAbsentOrInvalid(String value) {
        assertThat(RetryAfterUtils.parse(value, NOW)).isNull();
    }
}
