package com.example.activation.dto.response;

import com.example.activation.model.ActivationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Representação de uma solicitação de ativação devolvida pela API.
 *
 * @param id          identificador gerado pelo serviço
 * @param externalId  identificador no sistema de origem
 * @param customerId  cliente
 * @param productCode produto
 * @param status      status atual
 * @param attempts    chamadas já feitas ao provedor
 * @param lastError   motivo da última falha, quando houver
 * @param createdAt   data de criação (UTC)
 * @param updatedAt   data da última mudança (UTC)
 * @since 2026-10-29
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivationRequestResponse(
        @Schema(example = "0b7f3c1e-5d2a-4c4e-9a51-2f8d7b1e6c90") UUID id,
        @Schema(example = "EXT-12345") String externalId,
        @Schema(example = "CUST-10001") String customerId,
        @Schema(example = "INTERNET-500") String productCode,
        @Schema(example = "PENDING") ActivationStatus status,
        @Schema(description = "Chamadas já feitas ao provedor", example = "0") int attempts,
        @Schema(description = "Motivo da última falha; ausente quando não houve falha",
                example = "Provedor indisponível (HTTP 503)") String lastError,
        @Schema(example = "2026-10-26T14:03:11Z") Instant createdAt,
        @Schema(example = "2026-10-26T14:03:11Z") Instant updatedAt) {
}
