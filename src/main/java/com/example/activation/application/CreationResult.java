package com.example.activation.application;

import com.example.activation.domain.ActivationRequest;

/**
 * @param created {@code false} quando a chamada é uma repetição de uma solicitação já registrada.
 */
public record CreationResult(ActivationRequest request, boolean created) {

    static CreationResult created(ActivationRequest request) {
        return new CreationResult(request, true);
    }

    static CreationResult existing(ActivationRequest request) {
        return new CreationResult(request, false);
    }
}
