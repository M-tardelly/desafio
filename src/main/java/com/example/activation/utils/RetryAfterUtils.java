package com.example.activation.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Interpreta o header HTTP {@code Retry-After}, que pode vir em segundos ({@code 120}) ou como data
 * ({@code Wed, 21 Oct 2026 07:28:00 GMT}).
 *
 * @since 2026-10-29
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RetryAfterUtils {

    /**
     * Converte o valor do header em tempo de espera.
     *
     * @param headerValue valor do header, possivelmente {@code null}
     * @param now         instante atual, usado quando o valor é uma data
     * @return o tempo de espera, {@link Duration#ZERO} para datas passadas, ou {@code null} se ausente/inválido
     */
    public static Duration parse(String headerValue, Instant now) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        String value = headerValue.trim();
        try {
            return Duration.ofSeconds(Long.parseLong(value));
        } catch (NumberFormatException notSeconds) {
            return parseHttpDate(value, now);
        }
    }

    private static Duration parseHttpDate(String value, Instant now) {
        try {
            Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration delay = Duration.between(now, retryAt);
            return delay.isNegative() ? Duration.ZERO : delay;
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }
}
