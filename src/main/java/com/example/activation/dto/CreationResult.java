package com.example.activation.dto;

import com.example.activation.model.ActivationRequest;

/**
 * @param created {@code false} quando a chamada é uma repetição de uma solicitação já registrada.
 */
public record CreationResult(ActivationRequest request, boolean created) {

    public static CreationResult created(ActivationRequest request) {
        return new CreationResult(request, true);
    }

    public static CreationResult existing(ActivationRequest request) {
        return new CreationResult(request, false);
    }
}
