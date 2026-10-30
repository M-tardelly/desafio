package com.example.activation.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * Lançada quando o mesmo {@code externalId} já existe para o sistema consumidor com dados diferentes.
 * Convertida em HTTP 409 pelo {@code GlobalExceptionHandler}.
 *
 * @since 2026-10-29
 */
@Getter
public class DuplicateRequestConflictException extends RuntimeException {

    /**
     * Solicitação já registrada com o mesmo {@code externalId}, devolvida na resposta 409.
     */
    private final UUID existingRequestId;

    /**
     * Cria a exceção com a mensagem que vai no {@code detail} da resposta.
     *
     * @param externalId        identificador repetido
     * @param existingRequestId solicitação já registrada com esse identificador
     */
    public DuplicateRequestConflictException(String externalId, UUID existingRequestId) {
        super("Já existe uma solicitação com externalId '%s' com dados diferentes (customerId/productCode)."
                .formatted(externalId));
        this.existingRequestId = existingRequestId;
    }
}
