package com.careconnect.queue.domain;

import java.util.UUID;

public class QueueEntryNotFoundException extends RuntimeException {

    public QueueEntryNotFoundException(UUID id) {
        super("Queue entry %s not found".formatted(id));
    }

    public QueueEntryNotFoundException(String message) {
        super(message);
    }
}
