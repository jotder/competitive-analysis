package com.antigravity.companalysis.intelligence.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.antigravity.companalysis.analytics.BhkSupplyGap;
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
 * The {@code analyze-bhk-supply-gap} capability: map, for every BHK configuration in the market, how many
 * projects offer it and which do not — surfacing under-supplied configurations as competitive gaps.
 * Deterministic, model-free narration (the R1.2 analytics backfill). Abstains (UNAVAILABLE) when fewer than
 * two projects carry a BHK configuration.
 */
@Component
public class BhkSupplyGapCapability implements Capability {

    public static final String ID = "analyze-bhk-supply-gap";

    private static final CapabilitySpec SPEC = new CapabilitySpec(ID, 1,
            "Map BHK configuration supply across the market and surface under-supplied gaps.",
            ModelTier.SMALL, 0.5, Duration.ofSeconds(15),
            Set.of("sessionId"), Set.of(BhkSupplyGapTool.ID));

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
        Tool tool = ctx.tools().get(BhkSupplyGapTool.ID).orElse(null);
        if (tool == null) {
            return AgentResult.unavailable(ID, "bhk-supply-gap tool not available");
        }

        ToolResult result = tool.invoke(Map.of("sessionId", sessionId), ctx);
        if (!result.hasData()) {
            return AgentResult.unavailable(ID, "fewer than two projects with a BHK configuration to compare");
        }
        List<BhkSupplyGap> gaps = typed(result);

        return AgentResult.draft(ID, SPEC.version(), narrate(gaps), result.evidence(), List.of(),
                "deterministic BHK supply-gap mapping (R1.2 analytics — no model yet)", 0.0,
                ctx.effectiveTier(SPEC.defaultTier()), Map.of("bhkSupplyGaps", gaps));
    }

    private static List<BhkSupplyGap> typed(ToolResult result) {
        List<BhkSupplyGap> out = new ArrayList<>();
        if (result.value() instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof BhkSupplyGap g) {
                    out.add(g);
                }
            }
        }
        return out;
    }

    private static String narrate(List<BhkSupplyGap> gaps) {
        int marketSize = gaps.isEmpty() ? 0 : gaps.get(0).marketSize();
        long gapCount = gaps.stream().filter(BhkSupplyGap::isGap).count();
        StringBuilder sb = new StringBuilder("BHK supply across ").append(marketSize)
                .append(" projects (").append(gapCount).append(" configuration(s) not offered by all):");
        for (BhkSupplyGap g : gaps) {
            sb.append("\n- ").append(g.configuration()).append(": offered by ").append(g.supplyCount())
                    .append('/').append(g.marketSize());
            if (!g.offeredBy().isEmpty()) {
                sb.append(" (").append(String.join(", ", g.offeredBy())).append(')');
            }
            if (g.isGap()) {
                sb.append("; missing from ").append(String.join(", ", g.missingFrom()));
            }
        }
        return sb.toString();
    }
}
