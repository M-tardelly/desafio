package com.example.activation.model;

import com.example.activation.exception.InvalidStatusTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "activation_request")
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
    private Long version;

    protected ActivationRequest() {
    }

    public static ActivationRequest create(String clientId, String externalId, String customerId,
                                           String productCode, String correlationId, Instant now) {
        var request = new ActivationRequest();
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

    public boolean isReadyForProcessing(Instant now) {
        return status == ActivationStatus.PENDING && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
    }

    public void startProcessing(Instant now) {
        transitionTo(ActivationStatus.PROCESSING, now);
        attempts++;
        nextAttemptAt = null;
    }

    public void complete(Instant now) {
        transitionTo(ActivationStatus.COMPLETED, now);
        lastError = null;
    }

    public void fail(String reason, Instant now) {
        transitionTo(ActivationStatus.FAILED, now);
        lastError = truncate(reason);
    }

    public void scheduleRetry(Instant nextAttemptAt, String reason, Instant now) {
        transitionTo(ActivationStatus.PENDING, now);
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt);
        lastError = truncate(reason);
    }

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

    public UUID getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductCode() {
        return productCode;
    }

    public ActivationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
