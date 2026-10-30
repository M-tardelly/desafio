package com.example.activation.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Backoff exponencial com jitter ("equal jitter": metade fixa, metade aleatória) para evitar que
 * várias solicitações que falharam juntas voltem ao provedor no mesmo instante.
 *
 * @since 2026-10-29
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BackoffUtils {

    private static final int MAX_EXPONENT = 20;

    /**
     * Calcula a espera antes da próxima tentativa.
     *
     * @param attemptsSoFar  tentativas já feitas (a espera dobra a cada uma)
     * @param initialBackoff espera base
     * @param maxBackoff     espera máxima
     * @param retryAfter     tempo pedido pelo provedor; quando presente, tem prioridade sobre o cálculo
     * @return espera até a próxima tentativa
     */
    public static Duration nextDelay(int attemptsSoFar, Duration initialBackoff, Duration maxBackoff,
                                     Duration retryAfter) {
        if (retryAfter != null && !retryAfter.isNegative()) {
            return retryAfter.compareTo(maxBackoff) > 0 ? maxBackoff : retryAfter;
        }
        int exponent = Math.min(Math.max(attemptsSoFar - 1, 0), MAX_EXPONENT);
        long exponentialMillis = initialBackoff.toMillis() * (1L << exponent);
        long cappedMillis = Math.min(exponentialMillis, maxBackoff.toMillis());
        long fixedPart = cappedMillis / 2;
        long jitter = ThreadLocalRandom.current().nextLong(cappedMillis - fixedPart + 1);
        return Duration.ofMillis(fixedPart + jitter);
    }
}
