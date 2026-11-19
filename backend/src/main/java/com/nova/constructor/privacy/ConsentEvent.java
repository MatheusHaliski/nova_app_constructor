package com.nova.constructor.privacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registro imutável de cada concessão ou revogação. Serve de prova do consentimento (art. 8º, §2º) e de
 * responsabilização (art. 6º, X). O estado atual é o evento mais recente de cada finalidade.
 */
@Entity
@Table(name = "consent_events")
public class ConsentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", nullable = false, length = 36)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ConsentPurpose purpose;

    @Column(nullable = false)
    private boolean granted;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ConsentEvent() {}

    public ConsentEvent(UUID userId, ConsentPurpose purpose, boolean granted, Instant occurredAt) {
        this.userId = userId;
        this.purpose = purpose;
        this.granted = granted;
        this.occurredAt = occurredAt;
    }

    public ConsentPurpose getPurpose() { return purpose; }
    public boolean isGranted() { return granted; }
    public Instant getOccurredAt() { return occurredAt; }
}
