package com.example.activation.dto;

/**
 * Dados de entrada da criação, já com o sistema consumidor identificado pelo token.
 */
public record CreateActivationCommand(
        String clientId,
        String externalId,
        String customerId,
        String productCode,
        String correlationId) {
}
