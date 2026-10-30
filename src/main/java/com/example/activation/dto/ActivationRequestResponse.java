package com.example.activation.dto;

import com.example.activation.model.ActivationStatus;
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
}
