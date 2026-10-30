package com.example.activation.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Identificador de correlação: recebido ou gerado por requisição, guardado no MDC (aparece em todos os logs)
 * e propagado ao provedor externo.
 *
 * @since 2026-10-29
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CorrelationIdUtils {

    /**
     * Header HTTP recebido do cliente, devolvido na resposta e enviado ao provedor.
     */
    public static final String HEADER = "X-Correlation-Id";

    /**
     * Chave no MDC (usada no padrão de log e no JSON estruturado).
     */
    public static final String MDC_KEY = "correlationId";

    /**
     * Identificador da requisição em andamento.
     *
     * @return identificador de correlação da thread atual, ou {@code null}
     */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /**
     * Gera um identificador para requisições que chegam sem um.
     *
     * @return novo identificador (UUID sem hífens)
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Coloca o identificador no MDC até o {@code close()} (use com try-with-resources).
     *
     * @param value identificador; se {@code null}, um novo é gerado
     * @return recurso que remove o identificador do MDC ao fechar
     */
    public static MDC.MDCCloseable bind(String value) {
        return MDC.putCloseable(MDC_KEY, value != null ? value : generate());
    }
}
