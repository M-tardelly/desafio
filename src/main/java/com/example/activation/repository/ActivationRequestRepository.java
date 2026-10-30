package com.example.activation.repository;

import com.example.activation.model.ActivationRequest;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActivationRequestRepository
        extends JpaRepository<ActivationRequest, UUID>, JpaSpecificationExecutor<ActivationRequest> {

    Optional<ActivationRequest> findByIdAndClientId(UUID id, String clientId);

    Optional<ActivationRequest> findByClientIdAndExternalId(String clientId, String externalId);

    @Query("""
            select r.id from ActivationRequest r
            where r.status = com.example.activation.model.ActivationStatus.PENDING
              and r.nextAttemptAt <= :now
            order by r.nextAttemptAt
            """)
    List<UUID> findIdsReadyForProcessing(Instant now, Limit limit);

    @Query("""
            select r.id from ActivationRequest r
            where r.status = com.example.activation.model.ActivationStatus.PROCESSING
              and r.updatedAt < :threshold
            order by r.updatedAt
            """)
    List<UUID> findIdsStuckInProcessing(Instant threshold, Limit limit);
}
