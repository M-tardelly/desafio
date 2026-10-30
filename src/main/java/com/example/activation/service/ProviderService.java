package com.example.activation.service;

import com.example.activation.dto.provider.ProviderActivationResult;
import com.example.activation.model.ActivationRequest;

/**
 * Integração com o provedor externo de ativação.
 * Implementações não devem lançar exceções para falhas de comunicação: todo desfecho
 * é representado em {@link ProviderActivationResult}.
 */
public interface ProviderService {

    ProviderActivationResult activate(ActivationRequest request);
}
