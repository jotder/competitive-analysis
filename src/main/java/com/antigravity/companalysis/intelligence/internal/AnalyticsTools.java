package com.antigravity.companalysis.intelligence.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.antigravity.companalysis.catalog.CatalogService;
import com.antigravity.companalysis.catalog.CatalogService.EntityView;
import com.antigravity.companalysis.catalog.CatalogService.SessionView;
import com.antigravity.companalysis.reconciliation.ConsolidatedParameter;
import com.antigravity.companalysis.reconciliation.ConsolidatedParameter.Contribution;
import com.gamma.agentkernel.tool.Evidence;

/**
 * Shared helpers for the cross-entity analytics tools (amenity-premium, floor-rise, BHK-supply-gap): build
 * the {@code entityId -> display label} map the engines need to name projects, and turn a reconciled
 * parameter's anchor into a provenance-tagged {@link Evidence} (locator only; ADR-0008). Package-private —
 * these are an implementation detail of the {@code intelligence} module's tools.
 */
final class AnalyticsTools {

    private AnalyticsTools() {
    }

    /** The session's {@code entityId -> "Developer Project"} labels, in selection order. */
    static Map<UUID, String> labels(CatalogService catalog, UUID sessionId) {
        SessionView session = catalog.getSession(sessionId);
        Map<UUID, String> out = new LinkedHashMap<>();
        for (EntityView e : catalog.entities(session.entityIds())) {
            out.put(e.id(), label(e));
        }
        return out;
    }

    private static String label(EntityView e) {
        String project = (e.projectName() == null || e.projectName().isBlank()) ? "" : " " + e.projectName();
        return (e.developerName() + project).trim();
    }

    /** Evidence from a reconciled parameter's anchor contribution: value, kernel tier, CxO label, locator. */
    static Evidence anchorEvidence(ConsolidatedParameter c) {
        Contribution anchor = c.contributions().stream()
                .filter(Contribution::usedInAnchor)
                .findFirst()
                .orElse(null);
        String sourceRef = (anchor == null) ? null : anchor.sourceRef();
        double confidence = (anchor == null) ? 1.0 : anchor.confidence();
        return new Evidence(c.displayValue(), CredibilityTiers.toKernel(c.anchorTier()),
                c.anchorTier().name(), sourceRef, confidence, null);
    }
}
