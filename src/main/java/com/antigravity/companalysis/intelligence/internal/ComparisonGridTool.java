package com.antigravity.companalysis.intelligence.internal;

import com.antigravity.companalysis.catalog.CatalogService;
import com.antigravity.companalysis.catalog.CatalogService.EntityView;
import com.antigravity.companalysis.catalog.CatalogService.ObservationView;
import com.antigravity.companalysis.catalog.CatalogService.SessionView;
import com.antigravity.companalysis.shared.ParameterType;
import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.error.AgentError;
import com.gamma.agentkernel.tool.Evidence;
import com.gamma.agentkernel.tool.Tool;
import com.gamma.agentkernel.tool.ToolResult;
import com.gamma.agentkernel.tool.ToolSpec;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The kernel {@link Tool} that builds the comparison grid for a session by reading the {@code catalog}
 * module (the only catalog dependency the {@code intelligence} module is allowed). Deterministic: it
 * computes the grid; the LLM never does (ADR-0001). For each (entity, parameter) it selects the
 * most-credible factual observation using <b>CxO's own {@code CredibilityTier.rank()}</b> — so the app
 * never depends on the kernel enum's ordinal ordering (see {@link CredibilityTiers}) — and emits one
 * provenance-tagged {@link Evidence} per chosen value (source locator only; ADR-0008).
 *
 * <p>Registered into the agent's {@link com.gamma.agentkernel.tool.ToolRegistry} by {@code CxoAgent};
 * the {@code compare} capability invokes it via {@code ctx.tools()}.
 */
@Component
public class ComparisonGridTool implements Tool {

    public static final String ID = "get-comparison-grid";

    private static final ToolSpec SPEC = new ToolSpec(ID, 1,
            "Build the comparison grid (entities × parameters) for a session from the catalog: the "
                    + "most-credible factual value per cell, with its credibility tier and source.",
            Duration.ofSeconds(10));

    private final CatalogService catalog;

    public ComparisonGridTool(CatalogService catalog) {
        this.catalog = catalog;
    }

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public ToolResult invoke(Map<String, Object> args, AgentContext ctx) throws AgentError {
        UUID sessionId = sessionId(args);
        if (sessionId == null) {
            return ToolResult.noData();
        }

        SessionView session = catalog.getSession(sessionId);
        List<EntityView> entities = catalog.entities(session.entityIds());
        List<ObservationView> observations = catalog.baseObservations(session.entityIds());
        if (entities.isEmpty() || observations.isEmpty()) {
            return ToolResult.noData();
        }

        // Best factual observation per (entity, parameter), chosen by CxO's rank() then confidence.
        Map<UUID, Map<ParameterType, ObservationView>> best = new LinkedHashMap<>();
        for (ObservationView o : observations) {
            best.computeIfAbsent(o.entityId(), k -> new EnumMap<>(ParameterType.class))
                    .merge(o.parameter(), o, ComparisonGridTool::moreCredible);
        }

        List<ComparisonGrid.Cell> cells = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        for (EntityView entity : entities) {
            Map<ParameterType, ObservationView> byParam = best.get(entity.id());
            if (byParam == null) {
                continue;
            }
            String label = label(entity);
            for (ObservationView o : byParam.values()) { // EnumMap iterates in ParameterType order
                String value = value(o);
                String unit = unit(o);
                cells.add(new ComparisonGrid.Cell(entity.id(), label, o.parameter(), value, unit,
                        o.tier().name(), o.sourceRef()));
                evidence.add(new Evidence(value, CredibilityTiers.toKernel(o.tier()), o.tier().name(),
                        o.sourceRef(), confidence(o), o.observedAt()));
            }
        }

        if (cells.isEmpty()) {
            return ToolResult.noData();
        }
        return ToolResult.of(new ComparisonGrid(session.title(), session.microMarket(), cells), evidence);
    }

    /** Higher CxO tier rank wins; tie broken by higher confidence (keeps {@code a} when equal). */
    private static ObservationView moreCredible(ObservationView a, ObservationView b) {
        int byTier = Integer.compare(a.tier().rank(), b.tier().rank());
        if (byTier != 0) {
            return byTier > 0 ? a : b;
        }
        return confidence(a) >= confidence(b) ? a : b;
    }

    private static UUID sessionId(Map<String, Object> args) {
        Object raw = (args == null) ? null : args.get("sessionId");
        if (raw instanceof UUID u) {
            return u;
        }
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String label(EntityView e) {
        String project = (e.projectName() == null || e.projectName().isBlank()) ? "" : " " + e.projectName();
        return (e.developerName() + project).trim();
    }

    private static String value(ObservationView o) {
        return o.normalizedValue() != null ? o.normalizedValue().toPlainString() : o.rawValue();
    }

    private static String unit(ObservationView o) {
        return o.normalizedUnit() != null ? o.normalizedUnit() : o.rawUnit();
    }

    private static double confidence(ObservationView o) {
        BigDecimal c = o.confidence();
        return c == null ? 1.0 : c.doubleValue();
    }
}
