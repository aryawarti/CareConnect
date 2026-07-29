package com.careconnect.billing.domain;

import java.util.UUID;

public class InvoiceNotFoundException extends RuntimeException {

    public InvoiceNotFoundException(UUID id) {
        super("Invoice %s not found".formatted(id));
    }
}
