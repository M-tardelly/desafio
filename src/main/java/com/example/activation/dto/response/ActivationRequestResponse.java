package com.example.activation.dto.response;

import com.example.activation.model.ActivationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Representação de uma solicitação de ativação devolvida pela API.
 */
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
