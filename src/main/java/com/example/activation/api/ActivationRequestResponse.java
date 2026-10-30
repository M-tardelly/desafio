package com.example.activation.api;

import com.example.activation.domain.ActivationRequest;
import com.example.activation.domain.ActivationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivationRequestResponse(
        UUID id,
        String externalId,
        String customerId,
        String productCode,
        ActivationStatus status,
        int attempts,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {

    static ActivationRequestResponse from(ActivationRequest request) {
        return new ActivationRequestResponse(
                request.getId(),
                request.getExternalId(),
                request.getCustomerId(),
                request.getProductCode(),
                request.getStatus(),
                request.getAttempts(),
                request.getLastError(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}
