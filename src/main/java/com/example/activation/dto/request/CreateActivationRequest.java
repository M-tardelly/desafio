package com.example.activation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/v1/requests}.
 */
public record CreateActivationRequest(
        @NotBlank(message = REQUIRED)
        @Size(max = MAX_LENGTH, message = TOO_LONG)
        @Pattern(regexp = IDENTIFIER, message = INVALID_IDENTIFIER)
        String externalId,

        @NotBlank(message = REQUIRED)
        @Size(max = MAX_LENGTH, message = TOO_LONG)
        @Pattern(regexp = IDENTIFIER, message = INVALID_IDENTIFIER)
        String customerId,

        @NotBlank(message = REQUIRED)
        @Size(max = MAX_LENGTH, message = TOO_LONG)
        @Pattern(regexp = IDENTIFIER, message = INVALID_IDENTIFIER)
        String productCode) {

    static final int MAX_LENGTH = 64;
    static final String IDENTIFIER = "^[A-Za-z0-9._-]*$";

    static final String REQUIRED = "é obrigatório";
    static final String TOO_LONG = "deve ter no máximo " + MAX_LENGTH + " caracteres";
    static final String INVALID_IDENTIFIER = "deve conter apenas letras, números, '.', '_' ou '-'";
}
