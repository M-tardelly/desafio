package com.example.activation.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class DuplicateRequestConflictException extends RuntimeException {

    private final UUID existingRequestId;

    public DuplicateRequestConflictException(String externalId, UUID existingRequestId) {
        super("Já existe uma solicitação com externalId '%s' com dados diferentes (customerId/productCode)."
                .formatted(externalId));
        this.existingRequestId = existingRequestId;
    }
}
