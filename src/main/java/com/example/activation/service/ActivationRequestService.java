package com.example.activation.service;

import com.example.activation.dto.CreateActivationCommand;
import com.example.activation.dto.CreationResult;
import com.example.activation.model.ActivationRequest;
import com.example.activation.model.ActivationStatus;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ActivationRequestService {

    /**
     * Cria a solicitação ou, se o mesmo {@code externalId} já existir com o mesmo conteúdo,
     * devolve a existente ({@link CreationResult#created()} = {@code false}).
     *
     * @throws com.example.activation.exception.DuplicateRequestConflictException se existir com conteúdo diferente.
     */
    CreationResult create(CreateActivationCommand command);

    /**
     * @throws com.example.activation.exception.RequestNotFoundException se não existir ou pertencer a outro sistema.
     */
    ActivationRequest findForClient(UUID id, String clientId);

    Page<ActivationRequest> list(String clientId, ActivationStatus status, String customerId, int page, int size);
}
