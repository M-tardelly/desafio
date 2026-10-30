package com.example.activation.dto.command;

/**
 * Entrada da criação enviada pelo controller ao service, já com o sistema consumidor identificado pelo token.
 *
 * @param clientId      sistema consumidor autenticado
 * @param externalId    identificador no sistema de origem
 * @param customerId    cliente
 * @param productCode   produto
 * @param correlationId identificador de correlação da requisição
 * @since 2026-10-29
 */
public record CreateActivationCommand(
        String clientId,
        String externalId,
        String customerId,
        String productCode,
        String correlationId) {
}
