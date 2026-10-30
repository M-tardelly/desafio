package com.example.activation.dto.command;

import com.example.activation.model.ActivationRequest;

/**
 * Resultado da criação devolvido pelo service ao controller.
 *
 * @param created {@code false} quando a chamada é uma repetição de uma solicitação já registrada.
 */
public record CreateActivationResult(ActivationRequest request, boolean created) {

    public static CreateActivationResult created(ActivationRequest request) {
        return new CreateActivationResult(request, true);
    }

    public static CreateActivationResult existing(ActivationRequest request) {
        return new CreateActivationResult(request, false);
    }
}
