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

/**
 * Acesso a dados das solicitações de ativação.
 *
 * @since 2026-10-29
 */
@Repository
public interface ActivationRequestRepository
        extends JpaRepository<ActivationRequest, UUID>, JpaSpecificationExecutor<ActivationRequest> {

    /**
     * Busca uma solicitação garantindo que ela pertence ao sistema consumidor.
     *
     * @param id       solicitação
     * @param clientId sistema consumidor
     * @return a solicitação, se existir e pertencer ao sistema
     */
    Optional<ActivationRequest> findByIdAndClientId(UUID id, String clientId);

    /**
     * Busca a solicitação registrada com o identificador do sistema de origem.
     *
     * @param clientId   sistema consumidor
     * @param externalId identificador no sistema de origem
     * @return a solicitação já registrada com esse identificador, se houver
     */
    Optional<ActivationRequest> findByClientIdAndExternalId(String clientId, String externalId);

    /**
     * Solicitações prontas para serem enviadas ao provedor.
     *
     * @param now   instante atual
     * @param limit tamanho máximo do lote
     * @return ids PENDING cuja próxima tentativa já venceu, das mais antigas para as mais novas
     */
    @Query("""
            select r.id from ActivationRequest r
            where r.status = com.example.activation.model.ActivationStatus.PENDING
              and r.nextAttemptAt <= :now
            order by r.nextAttemptAt
            """)
    List<UUID> findIdsReadyForProcessing(Instant now, Limit limit);

    /**
     * Solicitações presas em PROCESSING (ex.: a instância caiu durante a chamada ao provedor).
     *
     * @param threshold solicitações em PROCESSING desde antes deste instante são consideradas presas
     * @param limit     tamanho máximo do lote
     * @return ids das solicitações presas
     */
    @Query("""
            select r.id from ActivationRequest r
            where r.status = com.example.activation.model.ActivationStatus.PROCESSING
              and r.updatedAt < :threshold
            order by r.updatedAt
            """)
    List<UUID> findIdsStuckInProcessing(Instant threshold, Limit limit);
}