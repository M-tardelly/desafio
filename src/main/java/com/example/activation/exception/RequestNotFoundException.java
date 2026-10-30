package com.example.activation.exception;

import java.util.UUID;

/**
 * Lançada quando a solicitação não existe ou pertence a outro sistema consumidor.
 * Convertida em HTTP 404 pelo {@code GlobalExceptionHandler}.
 *
 * @since 2026-10-29
 */
public class RequestNotFoundException extends RuntimeException {

    /**
     * Cria a exceção com a mensagem que vai no {@code detail} da resposta.
     *
     * @param id identificador procurado
     */
    public RequestNotFoundException(UUID id) {
        super("Solicitação %s não encontrada.".formatted(id));
    }
}
