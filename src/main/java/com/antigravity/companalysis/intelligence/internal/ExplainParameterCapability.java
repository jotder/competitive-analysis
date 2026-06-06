package com.antigravity.companalysis.intelligence.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.antigravity.companalysis.reconciliation.ConsolidatedParameter;
import com.antigravity.companalysis.reconciliation.ConsolidatedParameter.Contribution;
import com.antigravity.companalysis.shared.ParameterType;
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
 * The {@code explain-parameter} capability: explain how each figure was reconciled (ADR-0002) — the
 * consolidation method, the anchor tier, the spread, and which lower-credibility sources were set aside.
 * R1.2 narration is <b>deterministic and model-free</b> (a real narrator arrives in R1.3 behind the same
 * contract); every figure carries its credibility tier (ADR-0001). Abstains (UNAVAILABLE) when there is
 * nothing to consolidate rather than inventing values.
 *
 * <p>Optional {@code parameter} screen-context narrows the explanation to a single {@link ParameterType}.
 */
@Component
public class ExplainParameterCapability implements Capability {

    public static final String ID = "explain-parameter";

    private static final CapabilitySpec SPEC = new CapabilitySpec(ID, 1,
            "Explain how each parameter's canonical value was reconciled across sources, by credibility.",
            ModelTier.SMALL, 0.5, Duration.ofSeconds(15),
            Set.of("sessionId"), Set.of(ReconciliationTool.ID));

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
        Tool detail = ctx.tools().get(ReconciliationTool.ID).orElse(null);
        if (detail == null) {
            return AgentResult.unavailable(ID, "parameter-detail tool not available");
        }

        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("sessionId", sessionId);
        String parameter = request.context("parameter");
        if (parameter != null) {
            args.put("parameter", parameter);
        }

        ToolResult result = detail.invoke(args, ctx);
        List<ConsolidatedParameter> params = typed(result);
        if (!result.hasData() || params.isEmpty()) {
            return AgentResult.unavailable(ID, "no observations to reconcile for session " + sessionId);
        }

        return AgentResult.draft(ID, SPEC.version(), narrate(params), result.evidence(), List.of(),
                "deterministic reconciliation summary (R1.2 — no model yet)", 0.0,
                ctx.effectiveTier(SPEC.defaultTier()), Map.of("parameters", params));
    }

    private static List<ConsolidatedParameter> typed(ToolResult result) {
        List<ConsolidatedParameter> out = new ArrayList<>();
        if (result.value() instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof ConsolidatedParameter c) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /** Deterministic, tier-labelled reconciliation summary — no model, no re-derivation of the figures. */
    private static String narrate(List<ConsolidatedParameter> params) {
        StringBuilder sb = new StringBuilder("Reconciled ").append(params.size()).append(" parameter(s):");
        for (ConsolidatedParameter c : params) {
            sb.append("\n- ").append(c.parameter()).append(": ").append(c.displayValue());
            appendUnit(sb, c.unit());
            sb.append(" (").append(c.method()).append(", per ").append(c.anchorTier());
            if (c.spreadPct() != null && c.spreadPct().signum() > 0) {
                sb.append(", spread ").append(c.spreadPct().stripTrailingZeros().toPlainString()).append('%');
            }
            sb.append(')');
            for (Contribution k : c.contributions()) {
                sb.append("\n    • ").append(k.source()).append(' ').append(k.displayValue());
                appendUnit(sb, k.unit());
                sb.append(" (").append(k.tier());
                if (k.usedInAnchor()) {
                    sb.append(", used");
                }
                sb.append(')');
            }
        }
        return sb.toString();
    }

    private static void appendUnit(StringBuilder sb, String unit) {
        if (unit != null && !unit.isBlank()) {
            sb.append(' ').append(unit);
        }
    }
}
