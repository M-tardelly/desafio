package com.example.activation.mapper;

import com.example.activation.dto.command.CreateActivationCommand;
import com.example.activation.dto.provider.ProviderActivationRequest;
import com.example.activation.dto.request.CreateActivationRequest;
import com.example.activation.dto.response.ActivationRequestResponse;
import com.example.activation.dto.response.PageResponse;
import com.example.activation.model.ActivationRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

/**
 * Conversões entre a entidade {@link ActivationRequest} e os DTOs da API e do provedor.
 * A implementação é gerada pelo MapStruct em tempo de compilação.
 *
 * @since 2026-10-29
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ActivationRequestMapper {

    /**
     * Converte a entidade para a resposta da API.
     *
     * @param request entidade
     * @return representação devolvida pela API
     */
    ActivationRequestResponse toResponse(ActivationRequest request);

    /**
     * Monta o comando de criação a partir do corpo da requisição e do contexto autenticado.
     *
     * @param body          corpo recebido na API
     * @param clientId      sistema consumidor autenticado
     * @param correlationId identificador de correlação da requisição
     * @return comando de criação para o service
     */
    CreateActivationCommand toCommand(CreateActivationRequest body, String clientId, String correlationId);

    /**
     * Converte a entidade para o corpo enviado ao provedor.
     *
     * @param request entidade
     * @return corpo enviado ao provedor; o {@code requestId} é o id da solicitação
     */
    @Mapping(target = "requestId", source = "id")
    ProviderActivationRequest toProviderRequest(ActivationRequest request);

    /**
     * Converte uma página de entidades para o formato paginado da API.
     *
     * @param page página de entidades
     * @return página no formato da API
     */
    default PageResponse<ActivationRequestResponse> toPageResponse(Page<ActivationRequest> page) {
        return new PageResponse<>(page.getContent().stream().map(this::toResponse).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
