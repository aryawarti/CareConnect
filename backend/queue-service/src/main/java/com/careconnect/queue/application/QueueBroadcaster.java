package com.careconnect.queue.application;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events fan-out for live queue screens.
 *
 * SSE rather than WebSocket on purpose: the traffic is strictly one-way
 * (server → screen), SSE rides plain HTTP so it passes through the gateway
 * untouched, and browsers reconnect automatically. WebSocket would add a
 * protocol upgrade and a heavier client for no benefit here.
 *
 * Emitters are held per doctor's queue; a dead screen simply drops off the
 * list on the next failed send.
 */
@Component
public class QueueBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(QueueBroadcaster.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID doctorId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        subscribers.computeIfAbsent(doctorId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(doctorId, emitter));
        emitter.onTimeout(() -> remove(doctorId, emitter));
        emitter.onError(error -> remove(doctorId, emitter));
        try {
            // Immediate hello so the browser marks the stream open.
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove(doctorId, emitter);
        }
        return emitter;
    }

    /** Pushes the current queue snapshot to every screen watching this doctor. */
    public void broadcast(UUID doctorId, Object payload) {
        List<SseEmitter> emitters = subscribers.get(doctorId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("queue").data(payload));
            } catch (Exception e) {
                remove(doctorId, emitter);
            }
        }
    }

    /** Keeps proxies from closing idle streams. */
    public void heartbeat() {
        subscribers.forEach((doctorId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    remove(doctorId, emitter);
                }
            }
        });
    }

    public int subscriberCount() {
        return subscribers.values().stream().mapToInt(List::size).sum();
    }

    private void remove(UUID doctorId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.get(doctorId);
        if (emitters != null) {
            emitters.remove(emitter);
            log.debug("queue subscriber removed for doctor {}", doctorId);
        }
    }
}
