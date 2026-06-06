package com.antigravity.companalysis.intelligence.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.antigravity.companalysis.analytics.AmenityPremium;
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
 * The {@code analyze-amenity-premium} capability: explain what each project's price premium buys in
 * amenities, relative to the cheapest comparable project. R1.2-era narration is deterministic and model-free
 * (the analytics backfill). Abstains (UNAVAILABLE) when fewer than two projects can be compared.
 */
@Component
public class AmenityPremiumCapability implements Capability {

    public static final String ID = "analyze-amenity-premium";

    private static final CapabilitySpec SPEC = new CapabilitySpec(ID, 1,
            "Compare projects on the price premium paid for additional amenities.",
            ModelTier.SMALL, 0.5, Duration.ofSeconds(15),
            Set.of("sessionId"), Set.of(AmenityPremiumTool.ID));

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
        Tool tool = ctx.tools().get(AmenityPremiumTool.ID).orElse(null);
        if (tool == null) {
            return AgentResult.unavailable(ID, "amenity-premium tool not available");
        }

        ToolResult result = tool.invoke(Map.of("sessionId", sessionId), ctx);
        if (!result.hasData()) {
            return AgentResult.unavailable(ID, "fewer than two priced, amenity-bearing projects to compare");
        }
        List<AmenityPremium> premiums = typed(result);

        return AgentResult.draft(ID, SPEC.version(), narrate(premiums), result.evidence(), List.of(),
                "deterministic amenity-premium comparison (R1.2 analytics — no model yet)", 0.0,
                ctx.effectiveTier(SPEC.defaultTier()), Map.of("amenityPremiums", premiums));
    }

    private static List<AmenityPremium> typed(ToolResult result) {
        List<AmenityPremium> out = new ArrayList<>();
        if (result.value() instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof AmenityPremium p) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private static String narrate(List<AmenityPremium> premiums) {
        AmenityPremium base = premiums.stream().filter(AmenityPremium::baseline).findFirst()
                .orElse(premiums.get(0));
        StringBuilder sb = new StringBuilder("Amenity premium across ").append(premiums.size())
                .append(" projects (baseline: ").append(base.label()).append(" at ")
                .append(plain(base)).append("):");
        for (AmenityPremium p : premiums) {
            sb.append("\n- ").append(p.label()).append(": ").append(plain(p));
            if (p.baseline()) {
                sb.append(" (baseline, ").append(p.amenityCount()).append(" amenities)");
            } else {
                sb.append(" (+").append(p.pricePremiumPct().stripTrailingZeros().toPlainString()).append("%");
                if (p.extraAmenities().isEmpty()) {
                    sb.append(", no extra amenities over baseline)");
                } else {
                    sb.append(", adds ").append(String.join(", ", p.extraAmenities())).append(")");
                }
            }
        }
        return sb.toString();
    }

    private static String plain(AmenityPremium p) {
        String v = p.price().stripTrailingZeros().toPlainString();
        return (p.priceUnit() == null || p.priceUnit().isBlank()) ? v : v + " " + p.priceUnit();
    }
}
