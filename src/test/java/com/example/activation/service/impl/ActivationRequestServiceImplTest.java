package com.example.activation.service.impl;

import com.example.activation.dto.CreateActivationCommand;
import com.example.activation.exception.DuplicateRequestConflictException;
import com.example.activation.model.ActivationRequest;
import com.example.activation.repository.ActivationRequestRepository;
import com.example.activation.model.ActivationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivationRequestServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-26T14:03:11Z");

    @Mock
    private ActivationRequestRepository repository;

    private ActivationRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ActivationRequestServiceImpl(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private final CreateActivationCommand command =
            new CreateActivationCommand("system-a", "EXT-1", "CUST-1", "INTERNET-500", "corr-1");

    @Test
    void createsNewRequest() {
        when(repository.findByClientIdAndExternalId("system-a", "EXT-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(command);

        assertThat(result.created()).isTrue();
        assertThat(result.request().getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(result.request().getCreatedAt()).isEqualTo(NOW);
        assertThat(result.request().getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void returnsExistingRequestOnLegitimateRetry() {
        var existing = existingRequest("CUST-1", "INTERNET-500");
        when(repository.findByClientIdAndExternalId("system-a", "EXT-1")).thenReturn(Optional.of(existing));

        var result = service.create(command);

        assertThat(result.created()).isFalse();
        assertThat(result.request()).isSameAs(existing);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSameExternalIdWithDifferentContent() {
        var existing = existingRequest("CUST-1", "INTERNET-1000");
        when(repository.findByClientIdAndExternalId("system-a", "EXT-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(DuplicateRequestConflictException.class)
                .hasMessageContaining("EXT-1");
    }

    @Test
    void returnsWinnerWhenConcurrentInsertViolatesUniqueConstraint() {
        var winner = existingRequest("CUST-1", "INTERNET-500");
        when(repository.findByClientIdAndExternalId("system-a", "EXT-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk violation"));

        var result = service.create(command);

        assertThat(result.created()).isFalse();
        assertThat(result.request()).isSameAs(winner);
    }

    @Test
    void rejectsConcurrentInsertWithDifferentContent() {
        var winner = existingRequest("CUST-2", "INTERNET-500");
        when(repository.findByClientIdAndExternalId("system-a", "EXT-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk violation"));

        assertThatThrownBy(() -> service.create(command)).isInstanceOf(DuplicateRequestConflictException.class);
    }

    @Test
    void propagatesIntegrityViolationNotCausedByDuplicate() {
        var violation = new DataIntegrityViolationException("other constraint");
        when(repository.findByClientIdAndExternalId("system-a", "EXT-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(violation);

        assertThatThrownBy(() -> service.create(command)).isSameAs(violation);
    }

    private static ActivationRequest existingRequest(String customerId, String productCode) {
        return ActivationRequest.create("system-a", "EXT-1", customerId, productCode, "corr-0", NOW);
    }
}
