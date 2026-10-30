package com.example.activation.service.impl;

import com.example.activation.dto.command.ActivationRequestQuery;
import com.example.activation.dto.command.CreateActivationCommand;
import com.example.activation.dto.command.CreateActivationResult;
import com.example.activation.exception.DuplicateRequestConflictException;
import com.example.activation.exception.RequestNotFoundException;
import com.example.activation.model.ActivationRequest;
import com.example.activation.repository.ActivationRequestRepository;
import com.example.activation.service.ActivationRequestService;
import com.example.activation.utils.LogBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

import static com.example.activation.repository.ActivationRequestSpecifications.belongsTo;
import static com.example.activation.repository.ActivationRequestSpecifications.hasCustomer;
import static com.example.activation.repository.ActivationRequestSpecifications.hasStatus;

/**
 * Criação idempotente de solicitações (protegida pela constraint única de {@code client_id + external_id})
 * e consultas sempre filtradas pelo sistema consumidor.
 *
 * @since 2026-10-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivationRequestServiceImpl implements ActivationRequestService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ActivationRequestRepository repository;
    private final Clock clock;

    @Override
    public CreateActivationResult create(CreateActivationCommand command) {
        return repository.findByClientIdAndExternalId(command.clientId(), command.externalId())
                .map(existing -> replay(existing, command))
                .orElseGet(() -> insert(command));
    }

    @Override
    @Transactional(readOnly = true)
    public ActivationRequest findForClient(UUID id, String clientId) {
        return repository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new RequestNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ActivationRequest> list(ActivationRequestQuery query) {
        return repository.findAll(
                belongsTo(query.clientId()).and(hasStatus(query.status())).and(hasCustomer(query.customerId())),
                PageRequest.of(query.page(), query.size(), NEWEST_FIRST));
    }

    /**
     * Não é transacional de propósito: o INSERT roda em uma transação própria (saveAndFlush) para que,
     * se a constraint única for violada por uma requisição concorrente, a releitura do registro vencedor
     * aconteça em uma nova transação (no PostgreSQL uma transação com erro fica inutilizável).
     */
    private CreateActivationResult insert(CreateActivationCommand command) {
        ActivationRequest request = ActivationRequest.create(command.clientId(), command.externalId(),
                command.customerId(), command.productCode(), command.correlationId(), clock.instant());
        try {
            ActivationRequest saved = repository.saveAndFlush(request);
            LogBuilder.info(log, "Activation request created")
                    .with("requestId", saved.getId())
                    .with("externalId", saved.getExternalId())
                    .with("clientId", saved.getClientId())
                    .log();
            return CreateActivationResult.created(saved);
        } catch (DataIntegrityViolationException e) {
            ActivationRequest winner = repository.findByClientIdAndExternalId(command.clientId(), command.externalId())
                    .orElseThrow(() -> e);
            LogBuilder.info(log, "Concurrent duplicate detected, returning existing request")
                    .with("requestId", winner.getId())
                    .with("externalId", command.externalId())
                    .with("clientId", command.clientId())
                    .log();
            return replay(winner, command);
        }
    }

    private CreateActivationResult replay(ActivationRequest existing, CreateActivationCommand command) {
        if (!existing.hasSameContentAs(command.customerId(), command.productCode())) {
            throw new DuplicateRequestConflictException(command.externalId(), existing.getId());
        }
        return CreateActivationResult.existing(existing);
    }
}
