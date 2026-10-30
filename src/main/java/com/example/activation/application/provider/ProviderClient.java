package com.example.activation.application.provider;

/**
 * Implementações não devem lançar exceções para falhas de comunicação: todo desfecho
 * é representado em {@link ProviderResult}.
 */
public interface ProviderClient {

    ProviderResult activate(ActivationCommand command);
}
