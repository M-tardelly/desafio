package com.example.activation.service.impl;

import com.example.activation.dto.command.CreateActivationCommand;
import com.example.activation.dto.command.CreateActivationResult;
import com.example.activation.exception.DuplicateRequestConflictException;
import com.example.activation.exception.RequestNotFoundException;
import com.example.activation.model.ActivationRequest;
import com.example.activation.model.ActivationStatus;
import com.example.activation.repository.ActivationRequestRepository;
import com.example.activation.service.ActivationRequestService;
import com.example.activation.utils.LogBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivationRequestServiceImpl implements ActivationRequestService {

    private final ActivationRequestRepository repository;
    private final Clock clock;

    /**
     * Não é transacional de propósito: o INSERT roda em uma transação própria (saveAndFlush) para que,
     * se a constraint única for violada por uma requisição concorrente, a releitura do registro vencedor
     * aconteça em uma nova transação (no PostgreSQL uma transação com erro fica inutilizável).
     */
    @Override
    public CreateActivationResult create(CreateActivationCommand command) {
        Optional<ActivationRequest> existing =
                repository.findByClientIdAndExternalId(command.clientId(), command.externalId());
        if (existing.isPresent()) {
            return replay(existing.get(), command);
        }

        try {
            ActivationRequest request = ActivationRequest.create(command.clientId(), command.externalId(), command.customerId(),
                    command.productCode(), command.correlationId(), clock.instant());
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

    @Override
    @Transactional(readOnly = true)
    public ActivationRequest findForClient(UUID id, String clientId) {
        return repository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new RequestNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ActivationRequest> list(String clientId, ActivationStatus status, String customerId, int page, int size) {
        Specification<ActivationRequest> filter = (root, query, cb) -> cb.equal(root.get("clientId"), clientId);
        if (status != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (customerId != null && !customerId.isBlank()) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("customerId"), customerId));
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findAll(filter, pageable);
    }

    private CreateActivationResult replay(ActivationRequest existing, CreateActivationCommand command) {
        if (!existing.hasSameContentAs(command.customerId(), command.productCode())) {
            throw new DuplicateRequestConflictException(command.externalId(), existing.getId());
        }
        return CreateActivationResult.existing(existing);
    }
}
