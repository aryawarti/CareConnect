package com.careconnect.patient.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Value object — no identity of its own; lives in the patients table. */
@Embeddable
public record Address(
        @Column(name = "address_line1") String line1,
        @Column(name = "address_line2") String line2,
        String city,
        String state,
        @Column(name = "postal_code") String postalCode) {
}
