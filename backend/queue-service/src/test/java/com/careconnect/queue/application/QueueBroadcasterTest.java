package com.careconnect.queue.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The fan-out itself is simple; what these tests protect is the part that is
 * invisible until the system is behind a proxy — the heartbeat that stops an
 * idle stream being closed underneath a waiting-room board.
 *
 * No Spring context: QueueBroadcaster is a plain object holding a map of
 * emitters, and starting a container to exercise it would prove less, slower.
 */
class QueueBroadcasterTest {

    private final QueueBroadcaster broadcaster = new QueueBroadcaster();
    private final UUID doctor = UUID.randomUUID();

    @Test
    @DisplayName("a subscriber is registered and counted")
    void subscribeRegisters() {
        broadcaster.subscribe(doctor);

        assertThat(broadcaster.subscriberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("broadcasting to a doctor nobody is watching is a no-op, not an error")
    void broadcastWithoutSubscribersIsSafe() {
        broadcaster.broadcast(UUID.randomUUID(), "anything");

        assertThat(broadcaster.subscriberCount()).isZero();
    }

    @Test
    @DisplayName("the heartbeat leaves a live subscriber connected")
    void heartbeatKeepsLiveSubscribers() {
        broadcaster.subscribe(doctor);

        broadcaster.heartbeat();

        assertThat(broadcaster.subscriberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the heartbeat reaps a screen that went away")
    void heartbeatDropsDeadSubscribers() {
        SseEmitter emitter = broadcaster.subscribe(doctor);
        // A closed browser tab looks exactly like this from here: the emitter is
        // finished, so the next send throws and the subscriber must be dropped.
        // Without the heartbeat this is only noticed on the next real event,
        // which for a quiet queue can be hours away.
        emitter.complete();

        broadcaster.heartbeat();

        assertThat(broadcaster.subscriberCount()).isZero();
    }

    @Test
    @DisplayName("broadcasting also reaps a screen that went away")
    void broadcastDropsDeadSubscribers() {
        SseEmitter emitter = broadcaster.subscribe(doctor);
        emitter.complete();

        broadcaster.broadcast(doctor, "queue changed");

        assertThat(broadcaster.subscriberCount()).isZero();
    }

    @Test
    @DisplayName("the heartbeat is scheduled often enough to beat every proxy timeout")
    void heartbeatIsScheduledUnderTheTightestProxyTimeout() throws NoSuchMethodException {
        // Pinning configuration, not behaviour, and deliberately so: this method
        // was dead code for a while — present, correct, and never called, because
        // nothing scheduled it. That defect is invisible to a behavioural test
        // (heartbeat() works fine when you call it yourself) and only shows up in
        // production as streams dying after a minute of quiet.
        //
        // 60_000 is nginx's default proxy_read_timeout and the shortest of the
        // timeouts in play; Cloudflare's free tier drops at 100s.
        Method heartbeat = QueueBroadcaster.class.getMethod("heartbeat");
        Scheduled scheduled = heartbeat.getAnnotation(Scheduled.class);

        assertThat(scheduled)
            .as("heartbeat() must be @Scheduled or idle SSE streams get closed by proxies")
            .isNotNull();
        assertThat(scheduled.fixedDelay())
            .as("heartbeat interval must stay well under the 60s nginx default")
            .isPositive()
            .isLessThan(60_000L);
    }
}
