package com.careconnect.queue.domain;

public class InvalidQueueTransitionException extends RuntimeException {

    public InvalidQueueTransitionException(String message) {
        super(message);
    }
}
