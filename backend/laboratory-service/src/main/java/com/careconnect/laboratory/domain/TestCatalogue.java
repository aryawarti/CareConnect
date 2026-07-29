package com.careconnect.laboratory.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "test_catalogue")
public class TestCatalogue {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "specimen_type", nullable = false)
    private String specimenType;

    @Column(nullable = false)
    private String department;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "tat_minutes", nullable = false)
    private int tatMinutes;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "test_id")
    @OrderBy("displayOrder ASC")
    private List<TestAnalyte> analytes = new ArrayList<>();

    protected TestCatalogue() { }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSpecimenType() { return specimenType; }
    public String getDepartment() { return department; }
    public BigDecimal getPrice() { return price; }
    public int getTatMinutes() { return tatMinutes; }
    public boolean isActive() { return active; }
    public List<TestAnalyte> getAnalytes() { return analytes; }
}
