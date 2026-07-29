package com.careconnect.medicalrecord.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Immutable record of what a note said before it was corrected. */
@Entity
@Table(name = "encounter_amendments")
@EntityListeners(AuditingEntityListener.class)
public class Amendment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "previous_note", columnDefinition = "text")
    private String previousNote;

    @Column(nullable = false)
    private String reason;

    @CreatedBy
    @Column(name = "amended_by", updatable = false)
    private String amendedBy;

    @CreatedDate
    @Column(name = "amended_at", updatable = false)
    private Instant amendedAt;

    protected Amendment() { }

    Amendment(String previousNote, String reason) {
        this.previousNote = previousNote;
        this.reason = reason;
    }

    public String getPreviousNote() { return previousNote; }
    public String getReason() { return reason; }
    public Instant getAmendedAt() { return amendedAt; }
}
