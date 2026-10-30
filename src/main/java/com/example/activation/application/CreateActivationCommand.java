package com.example.activation.application;

public record CreateActivationCommand(
        String clientId,
        String externalId,
        String customerId,
        String productCode,
        String correlationId) {
}
