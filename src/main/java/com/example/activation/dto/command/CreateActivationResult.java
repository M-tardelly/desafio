package com.example.activation.dto.command;

import com.example.activation.model.ActivationRequest;

/**
 * Resultado da criação devolvido pelo service ao controller.
 *
 * @param request solicitação criada ou já existente
 * @param created {@code false} quando a chamada é uma repetição de uma solicitação já registrada
 * @since 2026-10-29
 */
public record CreateActivationResult(ActivationRequest request, boolean created) {

    /**
     * Resultado de uma criação nova (HTTP 201).
     *
     * @param request solicitação recém-criada
     * @return resultado com {@code created = true}
     */
    public static CreateActivationResult created(ActivationRequest request) {
        return new CreateActivationResult(request, true);
    }

    /**
     * Resultado de uma repetição com o mesmo conteúdo (HTTP 200).
     *
     * @param request solicitação que já existia
     * @return resultado com {@code created = false}
     */
    public static CreateActivationResult existing(ActivationRequest request) {
        return new CreateActivationResult(request, false);
    }
}
