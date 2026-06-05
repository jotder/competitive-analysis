package com.antigravity.companalysis.catalog.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.antigravity.companalysis.shared.AreaBasis;
import com.antigravity.companalysis.shared.CredibilityTier;
import com.antigravity.companalysis.shared.ParameterType;
import com.antigravity.companalysis.shared.SourceSystem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Append-only: one row per (parameter, source). The three different source values for one parameter are
 * three rows — never overwritten (ADR-0002). Maps to {@code parameter_observation} (V1).
 */
@Entity
@Table(name = "parameter_observation")
public class ParameterObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "entity_id", nullable = false, columnDefinition = "uuid")
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private ParameterType parameter;

    @Column(name = "raw_value", length = 512)
    private String rawValue;

    @Column(name = "raw_unit", length = 64)
    private String rawUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "area_basis", length = 32)
    private AreaBasis areaBasis;

    @Column(name = "normalized_value", precision = 18, scale = 4)
    private BigDecimal normalizedValue;

    @Column(name = "normalized_unit", length = 64)
    private String normalizedUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private SourceSystem source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CredibilityTier tier;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "source_ref", length = 2048)
    private String sourceRef;

    /** Non-null only for ASSUMPTION (what-if) observations; null for facts that fill the base grid. */
    @Column(name = "scenario_id", columnDefinition = "uuid")
    private UUID scenarioId;

    @CreationTimestamp
    @Column(name = "observed_at", updatable = false)
    private Instant observedAt;

    protected ParameterObservation() {
    }

    public ParameterObservation(UUID entityId, ParameterType parameter, String rawValue, String rawUnit,
                                AreaBasis areaBasis, BigDecimal normalizedValue, String normalizedUnit,
                                SourceSystem source, CredibilityTier tier, BigDecimal confidence,
                                String sourceRef, UUID scenarioId) {
        this.entityId = entityId;
        this.parameter = parameter;
        this.rawValue = rawValue;
        this.rawUnit = rawUnit;
        this.areaBasis = areaBasis;
        this.normalizedValue = normalizedValue;
        this.normalizedUnit = normalizedUnit;
        this.source = source;
        this.tier = tier;
        this.confidence = confidence;
        this.sourceRef = sourceRef;
        this.scenarioId = scenarioId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public ParameterType getParameter() {
        return parameter;
    }

    public String getRawValue() {
        return rawValue;
    }

    public String getRawUnit() {
        return rawUnit;
    }

    public AreaBasis getAreaBasis() {
        return areaBasis;
    }

    public BigDecimal getNormalizedValue() {
        return normalizedValue;
    }

    public String getNormalizedUnit() {
        return normalizedUnit;
    }

    public SourceSystem getSource() {
        return source;
    }

    public CredibilityTier getTier() {
        return tier;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public UUID getScenarioId() {
        return scenarioId;
    }

    public Instant getObservedAt() {
        return observedAt;
    }
}
