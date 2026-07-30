package com.careconnect.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class Role {

    /**
     * The four roles this system actually implements. LAB_TECHNICIAN went with
     * laboratory-service (ADR-010); RADIOLOGIST, PHARMACIST, NURSE, BILLING and
     * SUPER_ADMIN were declared for services that were never built and had zero
     * references anywhere — a permission model listing roles nothing enforces is
     * worse than a short one, because it implies checks that do not exist.
     */
    public static final String ADMIN = "ADMIN";
    public static final String DOCTOR = "DOCTOR";
    public static final String PATIENT = "PATIENT";
    public static final String STAFF = "STAFF";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    protected Role() { }

    public Role(String name) {
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
}
