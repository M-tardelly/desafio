package com.example.activation.infrastructure.provider;

import java.util.UUID;

record ProviderActivationRequest(UUID requestId, String customerId, String productCode) {
}
