package com.example.activation.dto.response;

/**
 * Item da lista {@code errors} das respostas de validação (Problem Details).
 */
public record FieldErrorResponse(String field, String message) {
}
