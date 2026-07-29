package com.careconnect.laboratory.domain;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID id) { super("Lab order %s not found".formatted(id)); }
    public OrderNotFoundException(String message) { super(message); }
}
