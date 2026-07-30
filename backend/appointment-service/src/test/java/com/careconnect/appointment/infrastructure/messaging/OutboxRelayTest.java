package com.careconnect.appointment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * The relay's failure policy, which is the part of the outbox that is easy to
 * get wrong in a way nothing notices until events go missing.
 *
 * The same relay exists in five services (byte-identical); this covers the logic
 * once. Consolidating those copies is a separate change — see the note in the
 * class Javadoc of OutboxRelay.
 */
class OutboxRelayTest {

    private static final short MAX_ATTEMPTS = 3;

    private final OutboxRepository outbox = mock(OutboxRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

    private OutboxRelay relay() {
        return new OutboxRelay(outbox, kafka, new SimpleMeterRegistry(), 100, MAX_ATTEMPTS);
    }

    private OutboxEvent event() {
        return new OutboxEvent("appointment.events", "AppointmentCompleted",
                UUID.randomUUID(), "{\"eventId\":\"e1\"}");
    }

    private void pending(OutboxEvent... events) {
        // Both parameters are int on the repository (the relay's short widens),
        // so both matchers must be anyInt() or the stub silently never matches.
        when(outbox.lockPending(anyInt(), anyInt())).thenReturn(List.of(events));
    }

    private void sendFailsWith(Throwable cause) {
        when(kafka.send(any(), any(), any())).thenReturn(CompletableFuture.failedFuture(cause));
    }

    @AfterEach
    void clearInterruptFlag() {
        // Thread.interrupted() reads AND clears — stop a flag set by one test
        // leaking into the next.
        Thread.interrupted();
    }

    @Test
    void marksEventPublishedOnSuccess() {
        OutboxEvent event = event();
        pending(event);
        when(kafka.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay().relay();

        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();
    }

    /**
     * The important one. A broker outage must not consume the retry budget, or a
     * long outage would abandon events that were never faulty — which is exactly
     * the data loss the outbox exists to prevent.
     */
    @Test
    void brokerUnavailableDoesNotConsumeAnAttempt() {
        OutboxEvent event = event();
        pending(event);
        sendFailsWith(new TimeoutException("no leader for partition"));

        relay().relay();

        assertThat(event.getAttempts()).isZero();
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void brokerUnavailableStopsTheBatchRatherThanHammeringIt() {
        OutboxEvent first = event();
        OutboxEvent second = event();
        pending(first, second);
        sendFailsWith(new TimeoutException("broker down"));

        relay().relay();

        // One attempt for the first event, then the batch aborts.
        verify(kafka).send(any(), any(), any());
        assertThat(second.getAttempts()).isZero();
    }

    /**
     * A payload the broker will never accept must not block everything queued
     * behind it — `lockPending` orders by created_at, so a poison event at the
     * head would otherwise stall the entire stream indefinitely.
     */
    @Test
    void aRecordSpecificFailureCountsAnAttemptAndLetsLaterEventsThrough() {
        OutboxEvent poison = event();
        OutboxEvent healthy = event();
        pending(poison, healthy);
        when(kafka.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RecordTooLargeException("message is 3MB")))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay().relay();

        assertThat(poison.getAttempts()).isEqualTo((short) 1);
        assertThat(poison.getPublishedAt()).isNull();
        assertThat(healthy.getPublishedAt())
                .as("a poison event must not block the events behind it")
                .isNotNull();
    }

    @Test
    void anEventIsAbandonedOnceItsAttemptsAreExhausted() {
        OutboxEvent event = event();
        pending(event);
        sendFailsWith(new RecordTooLargeException("still too large"));

        OutboxRelay relay = relay();
        for (int tick = 0; tick < MAX_ATTEMPTS; tick++) {
            relay.relay();
        }

        assertThat(event.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(event.getPublishedAt()).isNull();
    }

    /** Exhausted events are excluded by the query, not re-read and re-failed. */
    @Test
    void exhaustedEventsAreFilteredOutByTheQuery() {
        pending();

        relay().relay();

        verify(outbox).lockPending(MAX_ATTEMPTS, 100);
        verify(kafka, never()).send(any(), any(), any());
    }

    /**
     * Interruption is the ONE case where the flag should be restored. The bug this
     * replaces called Thread.currentThread().interrupt() on every failure,
     * including ordinary broker errors, marking the scheduler thread interrupted
     * and causing unrelated later operations on it to fail.
     */
    @Test
    @SuppressWarnings("unchecked")
    void interruptionRestoresTheFlagAndStops() throws Exception {
        OutboxEvent event = event();
        pending(event);
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get()).thenThrow(new InterruptedException("shutting down"));
        when(kafka.send(any(), any(), any())).thenReturn(future);

        relay().relay();

        assertThat(Thread.currentThread().isInterrupted())
                .as("an InterruptedException must leave the flag set for the thread's owner")
                .isTrue();
    }

    @Test
    void ordinaryBrokerFailureLeavesTheThreadUninterrupted() {
        pending(event());
        sendFailsWith(new TimeoutException("transient"));

        relay().relay();

        assertThat(Thread.currentThread().isInterrupted())
                .as("a broker error is not an interruption")
                .isFalse();
    }
}
