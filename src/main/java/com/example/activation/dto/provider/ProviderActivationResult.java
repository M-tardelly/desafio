package com.example.activation.dto.provider;

import java.time.Duration;

/**
 * Desfecho de uma chamada ao provedor externo, já classificado para decidir o próximo status.
 *
 * @since 2026-10-29
 */
public sealed interface ProviderActivationResult {

    /**
     * O provedor confirmou a ativação.
     */
    record Activated() implements ProviderActivationResult {
    }

    /**
     * O provedor recusou a solicitação; repetir a chamada não muda o resultado.
     *
     * @param reason motivo, guardado em {@code lastError}
     */
    record Rejected(String reason) implements ProviderActivationResult {
    }

    /**
     * Falha transitória ou resultado desconhecido (timeout); pode ser tentada novamente.
     *
     * @param reason     motivo, guardado em {@code lastError}
     * @param retryAfter tempo sugerido pelo provedor (header Retry-After), ou {@code null}
     */
    record TemporaryFailure(String reason, Duration retryAfter) implements ProviderActivationResult {
    }
}
