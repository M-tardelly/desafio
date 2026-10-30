package com.example.activation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/v1/requests}.
 *
 * @param externalId  identificador da solicitação no sistema de origem (único por sistema consumidor)
 * @param customerId  cliente que terá o produto ativado
 * @param productCode produto a ser ativado
 * @since 2026-10-29
 */
public record CreateActivationRequest(
        @Schema(description = "Identificador da solicitação no sistema de origem; repetir o mesmo valor é seguro",
                example = "EXT-12345")
        @NotBlank(message = REQUIRED)
        @Size(max = MAX_LENGTH, message = TOO_LONG)
        @Pattern(regexp = IDENTIFIER, message = INVALID_IDENTIFIER)
        String externalId,

        @Schema(description = "Cliente que terá o produto ativado", example = "CUST-10001")
        @NotBlank(message = REQUIRED)
        @Size(max = MAX_LENGTH, message = TOO_LONG)
        @Pattern(regexp = IDENTIFIER, message = INVALID_IDENTIFIER)
        String customerId,

        @Schema(description = "Produto a ser ativado", example = "INTERNET-500")
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
