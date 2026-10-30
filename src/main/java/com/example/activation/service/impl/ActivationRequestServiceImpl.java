package com.example.activation.service.impl;

import com.example.activation.dto.CreateActivationCommand;
import com.example.activation.dto.CreationResult;
import com.example.activation.exception.DuplicateRequestConflictException;
import com.example.activation.exception.RequestNotFoundException;
import com.example.activation.model.ActivationRequest;
import com.example.activation.model.ActivationStatus;
import com.example.activation.repository.ActivationRequestRepository;
import com.example.activation.service.ActivationRequestService;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    public CreationResult create(CreateActivationCommand command) {
        var existing = repository.findByClientIdAndExternalId(command.clientId(), command.externalId());
        if (existing.isPresent()) {
            return replay(existing.get(), command);
        }

        try {
            var request = ActivationRequest.create(command.clientId(), command.externalId(), command.customerId(),
                    command.productCode(), command.correlationId(), now());
            var saved = repository.saveAndFlush(request);
            log.info("Activation request created id={} externalId={}", saved.getId(), saved.getExternalId());
            return CreationResult.created(saved);
        } catch (DataIntegrityViolationException e) {
            var winner = repository.findByClientIdAndExternalId(command.clientId(), command.externalId())
                    .orElseThrow(() -> e);
            log.info("Concurrent duplicate detected for externalId={}, returning request {}",
                    command.externalId(), winner.getId());
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
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findAll(filter, pageable);
    }

    private CreationResult replay(ActivationRequest existing, CreateActivationCommand command) {
        if (!existing.hasSameContentAs(command.customerId(), command.productCode())) {
            throw new DuplicateRequestConflictException(command.externalId(), existing.getId());
        }
        return CreationResult.existing(existing);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
