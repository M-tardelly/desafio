package com.example.activation.utils;

import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Padroniza os logs da aplicação: mensagem curta + campos {@code chave=valor}.
 * <pre>{@code
 * LogBuilder.info(log, "Activation request created")
 *         .with("requestId", request.getId())
 *         .with("externalId", request.getExternalId())
 *         .log();
 * // Activation request created requestId=0b7f... externalId=EXT-12345
 * }</pre>
 * Os campos também são enviados como key-value pairs do SLF4J, que aparecem como atributos próprios no
 * log estruturado em JSON. O {@code correlationId} já vem do MDC e não precisa ser informado.
 *
 * @since 2026-10-29
 */
public final class LogBuilder {

    private final Logger logger;
    private final Level level;
    private final String message;
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private Throwable cause;

    private LogBuilder(Logger logger, Level level, String message) {
        this.logger = logger;
        this.level = level;
        this.message = message;
    }

    /**
     * Inicia um log de nível DEBUG.
     *
     * @param logger  logger da classe
     * @param message mensagem curta, sem valores variáveis
     * @return builder de log DEBUG
     */
    public static LogBuilder debug(Logger logger, String message) {
        return new LogBuilder(logger, Level.DEBUG, message);
    }

    /**
     * Inicia um log de nível INFO.
     *
     * @param logger  logger da classe
     * @param message mensagem curta, sem valores variáveis
     * @return builder de log INFO
     */
    public static LogBuilder info(Logger logger, String message) {
        return new LogBuilder(logger, Level.INFO, message);
    }

    /**
     * Inicia um log de nível WARN.
     *
     * @param logger  logger da classe
     * @param message mensagem curta, sem valores variáveis
     * @return builder de log WARN
     */
    public static LogBuilder warn(Logger logger, String message) {
        return new LogBuilder(logger, Level.WARN, message);
    }

    /**
     * Inicia um log de nível ERROR.
     *
     * @param logger  logger da classe
     * @param message mensagem curta, sem valores variáveis
     * @return builder de log ERROR
     */
    public static LogBuilder error(Logger logger, String message) {
        return new LogBuilder(logger, Level.ERROR, message);
    }

    /**
     * Adiciona um campo ao log; os campos aparecem na ordem em que foram adicionados.
     *
     * @param key   nome do campo (camelCase, ex.: {@code requestId})
     * @param value valor; {@code null} é registrado como "null"
     * @return este builder
     */
    public LogBuilder with(String key, Object value) {
        fields.put(key, value);
        return this;
    }

    /**
     * Inclui a exceção com stack trace no log.
     *
     * @param cause exceção a registrar
     * @return este builder
     */
    public LogBuilder exception(Throwable cause) {
        this.cause = cause;
        return this;
    }

    /**
     * Emite o log, se o nível estiver habilitado.
     */
    public void log() {
        if (!logger.isEnabledForLevel(level)) {
            return;
        }
        LoggingEventBuilder event = logger.atLevel(level);
        fields.forEach(event::addKeyValue);
        if (cause != null) {
            event = event.setCause(cause);
        }
        event.log(formattedMessage());
    }

    String formattedMessage() {
        if (fields.isEmpty()) {
            return message;
        }
        return fields.entrySet().stream()
                .map(field -> field.getKey() + "=" + field.getValue())
                .collect(Collectors.joining(" ", message + " ", ""));
    }
}
