package com.antigravity.companalysis.catalog.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A bounded comparison of 1–4 selected developer entities. Maps to {@code comparison_session} (V1). */
@Entity
@Table(name = "comparison_session")
public class ComparisonSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(name = "micro_market")
    private String microMarket;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_entity_ids", columnDefinition = "jsonb", nullable = false)
    private List<UUID> selectedEntityIds = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected ComparisonSession() {
    }

    public ComparisonSession(String title, String microMarket) {
        this.title = title;
        this.microMarket = microMarket;
    }

    /** @return true if added; false if the 1–4 cap is already reached. */
    public boolean addEntityId(UUID entityId) {
        if (selectedEntityIds.size() >= 4) {
            return false;
        }
        selectedEntityIds.add(entityId);
        return true;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getMicroMarket() {
        return microMarket;
    }

    public List<UUID> getSelectedEntityIds() {
        return selectedEntityIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
