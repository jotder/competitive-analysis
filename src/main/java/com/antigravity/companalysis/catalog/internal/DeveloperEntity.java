package com.antigravity.companalysis.catalog.internal;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A developer/project under comparison. Maps to {@code developer_entity} (V1). */
@Entity
@Table(name = "developer_entity")
public class DeveloperEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "developer_name", nullable = false)
    private String developerName;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "micro_market_area")
    private String microMarketArea;

    @Column(name = "rera_registration_number", length = 100)
    private String reraRegistrationNumber;

    @Column(name = "is_ours", nullable = false)
    private boolean ours;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected DeveloperEntity() {
    }

    public DeveloperEntity(String developerName, String projectName, String microMarketArea,
                           String reraRegistrationNumber, boolean ours) {
        this.developerName = developerName;
        this.projectName = projectName;
        this.microMarketArea = microMarketArea;
        this.reraRegistrationNumber = reraRegistrationNumber;
        this.ours = ours;
    }

    public UUID getId() {
        return id;
    }

    public String getDeveloperName() {
        return developerName;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getMicroMarketArea() {
        return microMarketArea;
    }

    public String getReraRegistrationNumber() {
        return reraRegistrationNumber;
    }

    public boolean isOurs() {
        return ours;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
