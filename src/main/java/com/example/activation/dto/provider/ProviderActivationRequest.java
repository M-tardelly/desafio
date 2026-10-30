package com.example.activation.dto.provider;

import java.util.UUID;

/**
 * Corpo enviado ao provedor externo em {@code POST /provider/v1/activate}.
 *
 * @param requestId   id da solicitação (o mesmo valor vai no header Idempotency-Key)
 * @param customerId  cliente
 * @param productCode produto
 * @since 2026-10-29
 */
public record ProviderActivationRequest(UUID requestId, String customerId, String productCode) {
}
