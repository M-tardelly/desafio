package com.example.activation.dto;

import java.time.Duration;

public sealed interface ProviderResult {

    record Activated() implements ProviderResult {
    }

    /**
     * O provedor recusou a solicitação; repetir a chamada não muda o resultado.
     */
    record Rejected(String reason) implements ProviderResult {
    }

    /**
     * Falha transitória ou resultado desconhecido (timeout); pode ser tentada novamente.
     *
     * @param retryAfter tempo sugerido pelo provedor (header Retry-After), ou {@code null}.
     */
    record TemporaryFailure(String reason, Duration retryAfter) implements ProviderResult {
    }
}
