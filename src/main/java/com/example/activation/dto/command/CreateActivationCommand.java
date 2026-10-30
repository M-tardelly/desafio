package com.example.activation.dto.command;

/**
 * Entrada da criação enviada pelo controller ao service, já com o sistema consumidor identificado pelo token.
 */
public record CreateActivationCommand(
        String clientId,
        String externalId,
        String customerId,
        String productCode,
        String correlationId) {
}
