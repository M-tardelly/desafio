package com.example.activation.dto;

import java.util.UUID;

/**
 * Corpo enviado ao provedor externo.
 */
public record ProviderActivationRequest(UUID requestId, String customerId, String productCode) {
}
