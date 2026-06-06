package com.antigravity.companalysis.intelligence.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.antigravity.companalysis.analytics.Discrepancy;
import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.AgentRequest;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.agent.Capability;
import com.gamma.agentkernel.agent.CapabilitySpec;
import com.gamma.agentkernel.error.AgentError;
import com.gamma.agentkernel.model.ModelTier;
import com.gamma.agentkernel.tool.Tool;
import com.gamma.agentkernel.tool.ToolResult;

/**
 * The {@code audit-discrepancies} capability: report where lower-credibility sources materially disagree
 * with the authoritative anchor (the RERA-vs-listing check; ADR-0002). R1.2 narration is deterministic and
 * model-free. Reports a clean "consistent" finding when observations exist but agree, and abstains
 * (UNAVAILABLE) only when there is nothing to audit.
 */
@Component
public class AuditDiscrepanciesCapability implements Capability {

    public static final String ID = "audit-discrepancies";

    private static final CapabilitySpec SPEC = new CapabilitySpec(ID, 1,
            "Audit a session for material gaps between authoritative and advertised values.",
            ModelTier.SMALL, 0.5, Duration.ofSeconds(15),
            Set.of("sessionId"), Set.of(DiscrepancyTool.ID));

    @Override
    public CapabilitySpec spec() {
        return SPEC;
    }

    @Override
    public AgentResult run(AgentRequest request, AgentContext ctx) throws AgentError {
        String sessionId = request.context("sessionId");
        if (sessionId == null) {
            return AgentResult.unavailable(ID, "no sessionId in request context");
        }
        Tool tool = ctx.tools().get(DiscrepancyTool.ID).orElse(null);
        if (tool == null) {
            return AgentResult.unavailable(ID, "discrepancy tool not available");
        }

        ToolResult result = tool.invoke(Map.of("sessionId", sessionId), ctx);
        if (!result.hasData()) {
            return AgentResult.unavailable(ID, "no observations to audit for session " + sessionId);
        }
        List<Discrepancy> discrepancies = typed(result);

        return AgentResult.draft(ID, SPEC.version(), narrate(discrepancies), result.evidence(), List.of(),
                "deterministic discrepancy audit (R1.2 — no model yet)", 0.0,
                ctx.effectiveTier(SPEC.defaultTier()), Map.of("discrepancies", discrepancies));
    }

    private static List<Discrepancy> typed(ToolResult result) {
        List<Discrepancy> out = new ArrayList<>();
        if (result.value() instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Discrepancy d) {
                    out.add(d);
                }
            }
        }
        return out;
    }

    private static String narrate(List<Discrepancy> discrepancies) {
        if (discrepancies.isEmpty()) {
            return "No material discrepancies (>=10%) found: every advertised value is within tolerance of "
                    + "its authoritative anchor.";
        }
        StringBuilder sb = new StringBuilder("Found ").append(discrepancies.size())
                .append(" material discrepancy(ies):");
        for (Discrepancy d : discrepancies) {
            String sign = d.deltaPct().signum() >= 0 ? "+" : "";
            sb.append("\n- ").append(d.parameter()).append(": ").append(d.otherSource())
                    .append(" (").append(d.otherTier()).append(") ").append(plain(d.otherValue()))
                    .append(" vs ").append(d.anchorTier()).append(" anchor ").append(plain(d.anchorValue()));
            appendUnit(sb, d.unit());
            sb.append(" (").append(sign).append(d.deltaPct().stripTrailingZeros().toPlainString()).append("%)");
        }
        return sb.toString();
    }

    private static void appendUnit(StringBuilder sb, String unit) {
        if (unit != null && !unit.isBlank()) {
            sb.append(' ').append(unit);
        }
    }

    private static String plain(java.math.BigDecimal v) {
        return v == null ? "n/a" : v.stripTrailingZeros().toPlainString();
    }
}
