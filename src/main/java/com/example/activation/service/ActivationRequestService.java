package com.example.activation.service;

import com.example.activation.dto.command.ActivationRequestQuery;
import com.example.activation.dto.command.CreateActivationCommand;
import com.example.activation.dto.command.CreateActivationResult;
import com.example.activation.model.ActivationRequest;
import org.springframework.data.domain.Page;

import java.util.UUID;

/**
 * Casos de uso de criação e consulta de solicitações de ativação.
 *
 * @since 2026-10-29
 */
public interface ActivationRequestService {

    /**
     * Cria a solicitação ou, se o mesmo {@code externalId} já existir com o mesmo conteúdo,
     * devolve a existente ({@link CreateActivationResult#created()} = {@code false}).
     *
     * @param command dados da solicitação e sistema consumidor
     * @return a solicitação e se ela foi criada agora
     * @throws com.example.activation.exception.DuplicateRequestConflictException se existir com conteúdo diferente
     */
    CreateActivationResult create(CreateActivationCommand command);

    /**
     * Busca uma solicitação do sistema consumidor.
     *
     * @param id       solicitação
     * @param clientId sistema consumidor
     * @return a solicitação
     * @throws com.example.activation.exception.RequestNotFoundException se não existir ou pertencer a outro sistema
     */
    ActivationRequest findForClient(UUID id, String clientId);

    /**
     * Solicitações do sistema consumidor, das mais recentes para as mais antigas.
     *
     * @param query filtros e paginação
     * @return página de solicitações
     */
    Page<ActivationRequest> list(ActivationRequestQuery query);
}
