package com.example.activation.dto.response;

import java.util.List;

/**
 * Página de resultados da listagem; evita expor o {@code Page} do Spring Data no contrato da API.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
