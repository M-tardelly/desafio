package com.example.activation.exception;

import java.util.UUID;

public class DuplicateRequestConflictException extends RuntimeException {

    private final UUID existingRequestId;

    public DuplicateRequestConflictException(String externalId, UUID existingRequestId) {
        super("Já existe uma solicitação com externalId '%s' com dados diferentes (customerId/productCode)."
                .formatted(externalId));
        this.existingRequestId = existingRequestId;
    }

    public UUID getExistingRequestId() {
        return existingRequestId;
    }
}
