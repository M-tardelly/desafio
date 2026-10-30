package com.example.activation.service;

/**
 * Envia ao provedor as solicitações pendentes e reagenda as que ficaram presas em processamento.
 *
 * @since 2026-10-29
 */
public interface ActivationProcessingService {

    /**
     * Envia ao provedor um lote de solicitações PENDING cuja próxima tentativa já venceu e grava o resultado.
     *
     * @return quantidade de solicitações enviadas ao provedor neste ciclo
     */
    int processDueRequests();

    /**
     * Devolve para PENDING (ou FAILED, se esgotou as tentativas) as solicitações presas em PROCESSING.
     *
     * @return quantidade de solicitações reagendadas
     */
    int recoverStuckRequests();
}
