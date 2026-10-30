package com.example.activation.dto.response;

/**
 * Item da lista {@code errors} das respostas de validação (Problem Details).
 *
 * @param field   campo ou parâmetro inválido
 * @param message motivo
 * @since 2026-10-29
 */
public record FieldErrorResponse(String field, String message) {
}
