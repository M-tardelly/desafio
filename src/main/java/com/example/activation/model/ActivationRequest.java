package com.example.activation.model;

import com.example.activation.exception.InvalidStatusTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sem setters: o status só muda pelos métodos de transição, que validam as regras de {@link ActivationStatus}.
 *
 * @since 2026-10-29
 */
@Entity
@Table(name = "activation_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivationRequest {

    private static final int LAST_ERROR_MAX_LENGTH = 500;

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false, length = 100)
    private String clientId;

    @Column(nullable = false, updatable = false, length = 64)
    private String externalId;

    @Column(nullable = false, updatable = false, length = 64)
    private String customerId;

    @Column(nullable = false, updatable = false, length = 64)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ActivationStatus status;

    @Column(nullable = false)
    private int attempts;

    private Instant nextAttemptAt;

    @Column(length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    @Column(updatable = false, length = 64)
    private String correlationId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Getter(AccessLevel.NONE)
    private Long version;

    /**
     * Cria uma solicitação PENDING, pronta para ser enviada ao provedor.
     *
     * @param clientId      sistema consumidor (dono da solicitação)
     * @param externalId    identificador no sistema de origem
     * @param customerId    cliente
     * @param productCode   produto
     * @param correlationId identificador de correlação da requisição de criação
     * @param now           instante da criação
     * @return nova solicitação
     */
    public static ActivationRequest create(String clientId, String externalId, String customerId,
                                           String productCode, String correlationId, Instant now) {
        ActivationRequest request = new ActivationRequest();
        request.id = UUID.randomUUID();
        request.clientId = Objects.requireNonNull(clientId);
        request.externalId = Objects.requireNonNull(externalId);
        request.customerId = Objects.requireNonNull(customerId);
        request.productCode = Objects.requireNonNull(productCode);
        request.correlationId = correlationId;
        request.status = ActivationStatus.PENDING;
        request.attempts = 0;
        request.nextAttemptAt = now;
        request.createdAt = now;
        request.updatedAt = now;
        return request;
    }

    /**
     * Indica se a solicitação pode ser enviada ao provedor agora.
     *
     * @param now instante atual
     * @return {@code true} se está PENDING e a próxima tentativa já venceu
     */
    public boolean isReadyForProcessing(Instant now) {
        return status == ActivationStatus.PENDING && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
    }

    /**
     * PENDING -> PROCESSING, contando uma nova tentativa.
     *
     * @param now instante da mudança
     * @throws InvalidStatusTransitionException se não estiver PENDING
     */
    public void startProcessing(Instant now) {
        transitionTo(ActivationStatus.PROCESSING, now);
        attempts++;
        nextAttemptAt = null;
    }

    /**
     * PROCESSING -> COMPLETED.
     *
     * @param now instante da mudança
     * @throws InvalidStatusTransitionException se não estiver PROCESSING
     */
    public void complete(Instant now) {
        transitionTo(ActivationStatus.COMPLETED, now);
        lastError = null;
    }

    /**
     * PROCESSING -> FAILED (estado final).
     *
     * @param reason motivo, guardado em {@code lastError}
     * @param now    instante da mudança
     * @throws InvalidStatusTransitionException se não estiver PROCESSING
     */
    public void fail(String reason, Instant now) {
        transitionTo(ActivationStatus.FAILED, now);
        lastError = truncate(reason);
    }

    /**
     * PROCESSING -> PENDING, agendando a próxima tentativa.
     *
     * @param nextAttemptAt quando a solicitação volta a ser processada
     * @param reason        motivo da falha temporária
     * @param now           instante da mudança
     * @throws InvalidStatusTransitionException se não estiver PROCESSING
     */
    public void scheduleRetry(Instant nextAttemptAt, String reason, Instant now) {
        transitionTo(ActivationStatus.PENDING, now);
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt);
        lastError = truncate(reason);
    }

    /**
     * Compara o conteúdo de uma nova requisição com o desta solicitação (detecção de duplicidade).
     *
     * @param customerId  cliente da nova requisição
     * @param productCode produto da nova requisição
     * @return {@code true} se a nova requisição é uma repetição desta solicitação
     */
    public boolean hasSameContentAs(String customerId, String productCode) {
        return this.customerId.equals(customerId) && this.productCode.equals(productCode);
    }

    private void transitionTo(ActivationStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(id, status, target);
        }
        status = target;
        updatedAt = now;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= LAST_ERROR_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
