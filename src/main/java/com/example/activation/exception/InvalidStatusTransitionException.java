package com.example.activation.exception;

import com.example.activation.model.ActivationStatus;

import java.util.UUID;

/**
 * Lançada quando uma mudança de status viola as transições permitidas em {@link ActivationStatus}.
 *
 * @since 2026-10-29
 */
public class InvalidStatusTransitionException extends RuntimeException {

    /**
     * Cria a exceção descrevendo a transição rejeitada.
     *
     * @param requestId solicitação
     * @param from      status atual
     * @param to        status pretendido
     */
    public InvalidStatusTransitionException(UUID requestId, ActivationStatus from, ActivationStatus to) {
        super("Transição de status inválida para a solicitação %s: %s -> %s".formatted(requestId, from, to));
    }
}
