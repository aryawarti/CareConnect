package com.careconnect.queue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Sequential, gap-free token numbers per doctor per day. A row-locked counter
 * rather than a sequence, because tokens must restart at 1 every morning and
 * be contiguous — patients notice missing numbers.
 */
@Entity
@Table(name = "token_counters")
@IdClass(TokenCounter.Key.class)
public class TokenCounter {

    @Id
    @Column(name = "doctor_id")
    private UUID doctorId;

    @Id
    @Column(name = "queue_date")
    private LocalDate queueDate;

    @Column(name = "last_number", nullable = false)
    private int lastNumber;

    protected TokenCounter() { }

    public TokenCounter(UUID doctorId, LocalDate queueDate) {
        this.doctorId = doctorId;
        this.queueDate = queueDate;
    }

    public int next() {
        return ++lastNumber;
    }

    public int getLastNumber() { return lastNumber; }

    public static class Key implements Serializable {
        private UUID doctorId;
        private LocalDate queueDate;

        public Key() { }

        public Key(UUID doctorId, LocalDate queueDate) {
            this.doctorId = doctorId;
            this.queueDate = queueDate;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(doctorId, key.doctorId) && Objects.equals(queueDate, key.queueDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(doctorId, queueDate);
        }
    }
}
