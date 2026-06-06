package com.antigravity.companalysis.intelligence.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.antigravity.companalysis.analytics.Discrepancy;
import com.antigravity.companalysis.analytics.DiscrepancyAnalyzer;
import com.antigravity.companalysis.reconciliation.ConsolidatedParameter;
import com.antigravity.companalysis.reconciliation.ReconciliationService;
import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.error.AgentError;
import com.gamma.agentkernel.tool.Evidence;
import com.gamma.agentkernel.tool.Tool;
import com.gamma.agentkernel.tool.ToolResult;
import com.gamma.agentkernel.tool.ToolSpec;

/**
 * The kernel {@link Tool} that surfaces the {@code analytics} RERA-vs-listing discrepancy engine: it
 * reconciles the session, runs the (pure) {@link DiscrepancyAnalyzer} over the result, and returns the
 * material gaps between each trusted anchor and the lower-credibility sources that disagree. Emits one
 * {@link Evidence} for the trusted anchor and one for the disagreeing source of every discrepancy
 * (locators only; ADR-0008).
 *
 * <p>Distinguishes "no observations at all" ({@code noData} ⇒ the capability abstains) from "observations
 * exist but agree" (a real, empty finding the capability can report as <em>consistent</em>).
 */
@Component
public class DiscrepancyTool implements Tool {

    public static final String ID = "find-discrepancies";

    private static final ToolSpec SPEC = new ToolSpec(ID, 1,
            "Flag material gaps (>=10%) between an authoritative value and the lower-credibility sources "
                    + "that disagree, per (entity, parameter).",
            Duration.ofSeconds(10));

    private final ReconciliationService reconciliation;

    public DiscrepancyTool(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public ToolResult invoke(Map<String, Object> args, AgentContext ctx) throws AgentError {
        UUID sessionId = ToolArgs.sessionId(args);
        if (sessionId == null) {
            return ToolResult.noData();
        }

        List<ConsolidatedParameter> consolidated = reconciliation.consolidate(sessionId);
        if (consolidated.isEmpty()) {
            return ToolResult.noData(); // nothing observed — abstain rather than claim "consistent"
        }

        List<Discrepancy> discrepancies = DiscrepancyAnalyzer.analyze(consolidated);
        List<Evidence> evidence = new ArrayList<>();
        for (Discrepancy d : discrepancies) {
            evidence.add(new Evidence(plain(d.anchorValue()), CredibilityTiers.toKernel(d.anchorTier()),
                    d.anchorTier().name(), d.anchorSourceRef(), 1.0, null));
            evidence.add(new Evidence(plain(d.otherValue()), CredibilityTiers.toKernel(d.otherTier()),
                    d.otherTier().name(), d.otherSourceRef(), 1.0, null));
        }
        return ToolResult.of(discrepancies, evidence);
    }

    private static String plain(java.math.BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }
}
