package com.example.activation.model;

/**
 * Status de uma solicitação de ativação: PENDING -> PROCESSING -> COMPLETED ou FAILED, com
 * PROCESSING -> PENDING quando uma falha temporária é agendada para nova tentativa.
 *
 * @since 2026-10-29
 */
public enum ActivationStatus {
    /**
     * Aguardando envio ao provedor (nova ou aguardando nova tentativa).
     */
    PENDING,

    /**
     * Chamada ao provedor em andamento.
     */
    PROCESSING,

    /**
     * Ativada pelo provedor (final).
     */
    COMPLETED,

    /**
     * Recusada pelo provedor ou tentativas esgotadas (final).
     */
    FAILED;

    /**
     * PROCESSING -> PENDING representa uma falha temporária que será tentada novamente.
     * COMPLETED e FAILED são estados finais.
     *
     * @param target status pretendido
     * @return {@code true} se a transição é permitida
     */
    public boolean canTransitionTo(ActivationStatus target) {
        return switch (this) {
            case PENDING -> target == PROCESSING;
            case PROCESSING -> target == COMPLETED || target == FAILED || target == PENDING;
            case COMPLETED, FAILED -> false;
        };
    }

    /**
     * Indica se o status é final (não muda mais).
     *
     * @return {@code true} para COMPLETED e FAILED
     */
    public boolean isFinal() {
        return this == COMPLETED || this == FAILED;
    }
}
