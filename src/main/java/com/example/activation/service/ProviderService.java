package com.example.activation.service;

import com.example.activation.dto.ProviderResult;
import com.example.activation.model.ActivationRequest;

/**
 * Integração com o provedor externo de ativação.
 * Implementações não devem lançar exceções para falhas de comunicação: todo desfecho
 * é representado em {@link ProviderResult}.
 */
public interface ProviderService {

    ProviderResult activate(ActivationRequest request);
}
