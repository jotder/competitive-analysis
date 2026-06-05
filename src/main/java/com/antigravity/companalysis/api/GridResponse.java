package com.antigravity.companalysis.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.antigravity.companalysis.catalog.CatalogService.ObservationView;
import com.antigravity.companalysis.shared.CredibilityTier;
import com.antigravity.companalysis.shared.ParameterType;

/**
 * The comparison grid: entities (rows) × parameters (columns). Each cell carries its status, the best
 * available credibility tier, and the underlying source observations (ADR-0002 — all values exposed, no
 * silent collapse). Consolidated/reconciled values are added in delivery step 3.
 */
public record GridResponse(UUID sessionId,
                           List<ParameterType> parameters,
                           List<Row> rows,
                           List<Gap> gaps) {

    public enum CellStatus {
        /** At least one authoritative (RERA) observation. */
        CREDIBLE,
        /** No authoritative value, but the user supplied one. */
        USER_PROVIDED,
        /** Only indicative (advertised) values. */
        INDICATIVE,
        /** No factual observation at all — needs gap-fill. */
        MISSING
    }

    public record Cell(CellStatus status, CredibilityTier bestTier, List<ObservationView> observations) {
    }

    public record Row(UUID entityId, String label, boolean ours, Map<ParameterType, Cell> cells) {
    }

    public record Gap(UUID entityId, ParameterType parameter) {
    }
}
