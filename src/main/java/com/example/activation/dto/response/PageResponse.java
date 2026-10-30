package com.example.activation.dto.response;

import java.util.List;

/**
 * Página de resultados da listagem; evita expor o {@code Page} do Spring Data no contrato da API.
 *
 * @param <T>           tipo dos itens
 * @param content       itens da página
 * @param page          número da página, começando em 0
 * @param size          tamanho da página
 * @param totalElements total de itens em todas as páginas
 * @param totalPages    total de páginas
 * @since 2026-10-29
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
