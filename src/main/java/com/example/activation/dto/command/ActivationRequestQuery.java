package com.example.activation.dto.command;

import com.example.activation.model.ActivationStatus;

/**
 * Filtros da listagem enviados pelo controller ao service.
 *
 * @param clientId   sistema consumidor (sempre filtrado)
 * @param status     filtro opcional por status
 * @param customerId filtro opcional por cliente
 * @param page       página, começando em 0
 * @param size       itens por página
 * @since 2026-10-29
 */
public record ActivationRequestQuery(
        String clientId,
        ActivationStatus status,
        String customerId,
        int page,
        int size) {
}
