package com.example.activation.service;

import com.example.activation.dto.provider.ProviderActivationResult;
import com.example.activation.model.ActivationRequest;

/**
 * Integração com o provedor externo de ativação.
 * Implementações não devem lançar exceções para falhas de comunicação: todo desfecho
 * é representado em {@link ProviderActivationResult}.
 *
 * @since 2026-10-29
 */
public interface ProviderService {

    /**
     * Solicita a ativação ao provedor, usando o id da solicitação como chave de idempotência.
     *
     * @param request solicitação a ativar
     * @return desfecho classificado da chamada
     */
    ProviderActivationResult activate(ActivationRequest request);
}
