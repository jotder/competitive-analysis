package com.antigravity.companalysis.intelligence.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.antigravity.companalysis.analytics.FloorRisePremium;
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
 * The {@code analyze-floor-rise} capability: rank the projects by the per-floor surcharge they charge to go
 * higher, and report each project's gap over the cheapest. Deterministic, model-free narration (the R1.2
 * analytics backfill). Abstains (UNAVAILABLE) when fewer than two projects carry a floor-rise premium.
 */
@Component
public class FloorRiseCapability implements Capability {

    public static final String ID = "analyze-floor-rise";

    private static final CapabilitySpec SPEC = new CapabilitySpec(ID, 1,
            "Rank projects by their per-floor surcharge (floor-rise premium).",
            ModelTier.SMALL, 0.5, Duration.ofSeconds(15),
            Set.of("sessionId"), Set.of(FloorRiseTool.ID));

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
        Tool tool = ctx.tools().get(FloorRiseTool.ID).orElse(null);
        if (tool == null) {
            return AgentResult.unavailable(ID, "floor-rise tool not available");
        }

        ToolResult result = tool.invoke(Map.of("sessionId", sessionId), ctx);
        if (!result.hasData()) {
            return AgentResult.unavailable(ID, "fewer than two projects with a floor-rise premium to rank");
        }
        List<FloorRisePremium> premiums = typed(result);

        return AgentResult.draft(ID, SPEC.version(), narrate(premiums), result.evidence(), List.of(),
                "deterministic floor-rise ranking (R1.2 analytics — no model yet)", 0.0,
                ctx.effectiveTier(SPEC.defaultTier()), Map.of("floorRisePremiums", premiums));
    }

    private static List<FloorRisePremium> typed(ToolResult result) {
        List<FloorRisePremium> out = new ArrayList<>();
        if (result.value() instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof FloorRisePremium p) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private static String narrate(List<FloorRisePremium> premiums) {
        StringBuilder sb = new StringBuilder("Floor-rise premium across ").append(premiums.size())
                .append(" projects (highest first):");
        for (FloorRisePremium p : premiums) {
            sb.append("\n- ").append(p.label()).append(": ").append(plain(p));
            if (p.highest()) {
                sb.append(" (highest");
            } else if (p.lowest()) {
                sb.append(" (lowest");
            } else {
                sb.append(" (");
            }
            if (!p.lowest() && p.deltaVsLowestPct() != null) {
                sb.append(p.highest() ? ", " : "").append("+")
                        .append(p.deltaVsLowestPct().stripTrailingZeros().toPlainString()).append("% vs lowest");
            }
            sb.append(')');
        }
        return sb.toString();
    }

    private static String plain(FloorRisePremium p) {
        String v = p.premium().stripTrailingZeros().toPlainString();
        return (p.unit() == null || p.unit().isBlank()) ? v : v + " " + p.unit();
    }
}
