package com.example.activation.dto.provider;

import java.time.Duration;

/**
 * Desfecho de uma chamada ao provedor externo, já classificado para decidir o próximo status.
 */
public sealed interface ProviderActivationResult {

    record Activated() implements ProviderActivationResult {
    }

    /**
     * O provedor recusou a solicitação; repetir a chamada não muda o resultado.
     */
    record Rejected(String reason) implements ProviderActivationResult {
    }

    /**
     * Falha transitória ou resultado desconhecido (timeout); pode ser tentada novamente.
     *
     * @param retryAfter tempo sugerido pelo provedor (header Retry-After), ou {@code null}.
     */
    record TemporaryFailure(String reason, Duration retryAfter) implements ProviderActivationResult {
    }
}
