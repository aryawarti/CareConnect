package com.careconnect.medicalrecord.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One recorded read of clinical data.
 *
 * Deliberately has no setters and no update path. The only operations are
 * "create" and "read": an audit row that the observed party can amend is not
 * evidence of anything. It is also not an {@code @EntityListeners} audited
 * entity like the rest of the domain — those track who last *changed* a row,
 * which is the opposite question.
 */
@Entity
@Table(name = "record_access_log")
public class RecordAccessEntry {

    @Id
    @GeneratedValue
    private UUID id;

    /** The identity subject (JWT sub) — the account answerable for the read. */
    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_role", nullable = false, updatable = false, length = 20)
    private String actorRole;

    @Column(name = "actor_name", updatable = false, length = 160)
    private String actorName;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    /** Null for list reads, which are not about one encounter. */
    @Column(name = "encounter_id", updatable = false)
    private UUID encounterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private RecordAccessAction action;

    @Column(name = "self_access", nullable = false, updatable = false)
    private boolean selfAccess;

    @Column(name = "accessed_at", nullable = false, updatable = false)
    private Instant accessedAt = Instant.now();

    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    protected RecordAccessEntry() { }

    public RecordAccessEntry(UUID actorUserId, String actorRole, String actorName,
                             UUID patientId, UUID encounterId, RecordAccessAction action,
                             boolean selfAccess, String correlationId) {
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.actorName = actorName;
        this.patientId = patientId;
        this.encounterId = encounterId;
        this.action = action;
        this.selfAccess = selfAccess;
        this.correlationId = correlationId;
    }

    public UUID getId() { return id; }
    public UUID getActorUserId() { return actorUserId; }
    public String getActorRole() { return actorRole; }
    public String getActorName() { return actorName; }
    public UUID getPatientId() { return patientId; }
    public UUID getEncounterId() { return encounterId; }
    public RecordAccessAction getAction() { return action; }
    public boolean isSelfAccess() { return selfAccess; }
    public Instant getAccessedAt() { return accessedAt; }
    public String getCorrelationId() { return correlationId; }
}
