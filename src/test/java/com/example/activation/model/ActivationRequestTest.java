package com.example.activation.model;

import com.example.activation.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivationRequestTest {

    private static final Instant NOW = Instant.parse("2026-10-26T14:03:11Z");

    private static ActivationRequest newRequest() {
        return ActivationRequest.create("system-a", "EXT-1", "CUST-1", "INTERNET-500", "corr-1", NOW);
    }

    @Test
    void startsPendingAndReadyForProcessing() {
        var request = newRequest();

        assertThat(request.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(request.getAttempts()).isZero();
        assertThat(request.getCreatedAt()).isEqualTo(NOW);
        assertThat(request.isReadyForProcessing(NOW)).isTrue();
    }

    @Test
    void completesAfterProcessing() {
        var request = newRequest();
        var later = NOW.plusSeconds(5);

        request.startProcessing(NOW);
        request.complete(later);

        assertThat(request.getStatus()).isEqualTo(ActivationStatus.COMPLETED);
        assertThat(request.getAttempts()).isEqualTo(1);
        assertThat(request.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    void temporaryFailureGoesBackToPendingUntilNextAttempt() {
        var request = newRequest();
        var retryAt = NOW.plus(Duration.ofSeconds(30));

        request.startProcessing(NOW);
        request.scheduleRetry(retryAt, "HTTP 500", NOW);

        assertThat(request.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(request.getLastError()).isEqualTo("HTTP 500");
        assertThat(request.isReadyForProcessing(NOW)).isFalse();
        assertThat(request.isReadyForProcessing(retryAt)).isTrue();

        request.startProcessing(retryAt);
        assertThat(request.getAttempts()).isEqualTo(2);
    }

    @Test
    void cannotCompleteWithoutProcessing() {
        var request = newRequest();

        assertThatThrownBy(() -> request.complete(NOW))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("PENDING -> COMPLETED");
    }

    @ParameterizedTest
    @EnumSource(value = ActivationStatus.class, names = {"COMPLETED", "FAILED"})
    void finalStatesCannotChange(ActivationStatus finalStatus) {
        var request = newRequest();
        request.startProcessing(NOW);
        if (finalStatus == ActivationStatus.COMPLETED) {
            request.complete(NOW);
        } else {
            request.fail("HTTP 400", NOW);
        }

        assertThatThrownBy(() -> request.startProcessing(NOW)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> request.scheduleRetry(NOW, "retry", NOW))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(request.getStatus()).isEqualTo(finalStatus);
    }

    @Test
    void comparesContentForDuplicateDetection() {
        var request = newRequest();

        assertThat(request.hasSameContentAs("CUST-1", "INTERNET-500")).isTrue();
        assertThat(request.hasSameContentAs("CUST-1", "INTERNET-1000")).isFalse();
        assertThat(request.hasSameContentAs("CUST-2", "INTERNET-500")).isFalse();
    }

    @Test
    void truncatesLongErrorMessages() {
        var request = newRequest();
        request.startProcessing(NOW);

        request.fail("x".repeat(1_000), NOW);

        assertThat(request.getLastError()).hasSize(500);
    }
}
