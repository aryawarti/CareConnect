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

    public static final String ADMIN = "ADMIN";
    public static final String DOCTOR = "DOCTOR";
    public static final String PATIENT = "PATIENT";
    public static final String STAFF = "STAFF";
    public static final String LAB_TECHNICIAN = "LAB_TECHNICIAN";
    public static final String RADIOLOGIST = "RADIOLOGIST";
    public static final String PHARMACIST = "PHARMACIST";
    public static final String NURSE = "NURSE";
    public static final String BILLING = "BILLING";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

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
