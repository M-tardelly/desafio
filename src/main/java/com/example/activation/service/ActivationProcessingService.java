package com.example.activation.service;

/**
 * Envia ao provedor as solicitações pendentes e reagenda as que ficaram presas em processamento.
 */
public interface ActivationProcessingService {

    /**
     * @return quantidade de solicitações enviadas ao provedor neste ciclo.
     */
    int processDueRequests();

    /**
     * @return quantidade de solicitações presas em PROCESSING que foram reagendadas.
     */
    int recoverStuckRequests();
}
