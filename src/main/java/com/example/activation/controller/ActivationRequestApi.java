package com.example.activation.controller;

import com.example.activation.dto.request.CreateActivationRequest;
import com.example.activation.dto.response.ActivationRequestResponse;
import com.example.activation.dto.response.PageResponse;
import com.example.activation.model.ActivationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Contrato documentado (OpenAPI) dos endpoints de solicitações de ativação. Mantém as anotações de
 * documentação fora do controller, que fica só com o mapeamento HTTP.
 * <p>
 * As validações ({@code @Valid}, {@code @Min}, {@code @Max}) ficam aqui porque o Bean Validation exige que
 * restrições de parâmetros sejam declaradas no método da interface, e não na implementação.
 *
 * @since 2026-10-29
 */
@Tag(name = "Solicitações de ativação", description = "Criação e consulta de solicitações de ativação de produtos")
@ApiResponse(responseCode = "401", description = "Token ausente, expirado ou inválido", content = @Content)
@ApiResponse(responseCode = "403", description = "Token sem o escopo necessário", content = @Content)
@ApiResponse(responseCode = "500", description = "Erro inesperado (sem detalhes internos)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public interface ActivationRequestApi {

    /**
     * Cria uma solicitação de ativação ou devolve a existente quando a chamada é uma repetição.
     *
     * @param body     dados da solicitação
     * @param clientId sistema consumidor autenticado
     * @return 201 com a solicitação criada ou 200 com a já existente
     */
    @Operation(summary = "Cria uma solicitação de ativação", description = """
            Registra a solicitação como PENDING e responde imediatamente; a ativação no provedor é feita em \
            segundo plano. Repetir a chamada com o mesmo externalId e o mesmo conteúdo é seguro: devolve 200 com o \
            registro existente. Requer o escopo activation:write.""")
    @ApiResponse(responseCode = "201", description = "Solicitação criada",
            headers = @Header(name = "Location", description = "URL da solicitação criada"))
    @ApiResponse(responseCode = "200", description = "Repetição: solicitação já existente com o mesmo conteúdo")
    @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Mesmo externalId com conteúdo diferente",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<ActivationRequestResponse> create(@Valid CreateActivationRequest body, String clientId);

    /**
     * Consulta uma solicitação do sistema consumidor.
     *
     * @param id       identificador da solicitação
     * @param clientId sistema consumidor autenticado
     * @return a solicitação com o status atual
     */
    @Operation(summary = "Consulta uma solicitação pelo id",
            description = "Retorna a solicitação e o status atual. Requer o escopo activation:read.")
    @ApiResponse(responseCode = "200", description = "Solicitação encontrada")
    @ApiResponse(responseCode = "400", description = "Id em formato inválido",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Solicitação inexistente ou de outro sistema",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ActivationRequestResponse get(@Parameter(description = "Id da solicitação") UUID id, String clientId);

    /**
     * Lista as solicitações do sistema consumidor, das mais recentes para as mais antigas.
     *
     * @param status     filtro opcional por status
     * @param customerId filtro opcional por cliente
     * @param page       página (começa em 0)
     * @param size       tamanho da página (1 a 100)
     * @param clientId   sistema consumidor autenticado
     * @return página de solicitações
     */
    @Operation(summary = "Lista solicitações com filtros e paginação",
            description = "Ordenadas por data de criação, da mais recente. Requer o escopo activation:read.")
    @ApiResponse(responseCode = "200", description = "Página de solicitações")
    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    PageResponse<ActivationRequestResponse> list(
            @Parameter(description = "Filtra pelo status") ActivationStatus status,
            @Parameter(description = "Filtra pelo cliente") String customerId,
            @Parameter(description = "Página, começando em 0") @Min(0) int page,
            @Parameter(description = "Itens por página (1 a 100)") @Min(1) @Max(100) int size,
            String clientId);
}
