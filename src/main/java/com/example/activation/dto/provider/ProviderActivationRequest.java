package com.example.activation.dto.provider;

import java.util.UUID;

/**
 * Corpo enviado ao provedor externo em {@code POST /provider/v1/activate}.
 */
public record ProviderActivationRequest(UUID requestId, String customerId, String productCode) {
}
