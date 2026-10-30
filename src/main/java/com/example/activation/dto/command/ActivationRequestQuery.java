package com.example.activation.dto.command;

import com.example.activation.model.ActivationStatus;

/**
 * Filtros da listagem enviados pelo controller ao service.
 *
 * @param status     opcional
 * @param customerId opcional
 */
public record ActivationRequestQuery(
        String clientId,
        ActivationStatus status,
        String customerId,
        int page,
        int size) {
}
