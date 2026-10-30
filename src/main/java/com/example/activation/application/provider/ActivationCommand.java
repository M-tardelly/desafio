package com.example.activation.application.provider;

import java.util.UUID;

/**
 * @param requestId também é usado como chave de idempotência no provedor.
 */
public record ActivationCommand(UUID requestId, String customerId, String productCode, String correlationId) {
}
