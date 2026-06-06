package com.antigravity.companalysis.analytics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.antigravity.companalysis.reconciliation.ConsolidatedParameter;
import com.antigravity.companalysis.reconciliation.ReconciliationService;
import com.antigravity.companalysis.shared.ParameterType;

/**
 * The BHK-supply-gap engine: across a session's projects, it reads each project's
 * {@link ParameterType#BHK_CONFIG} set and, for every distinct configuration in the market, counts how many
 * projects offer it and names those that do not. Rarely-offered configurations (low supply) bubble to the
 * top as the competitive gaps. Deterministic set arithmetic (ADR-0001) — the agent narrates which gap is an
 * opportunity, the engine only counts.
 *
 * <p>Needs ≥2 projects that carry a BHK configuration; otherwise {@link #analyze} returns empty and the tool
 * abstains. Configurations are matched case- and space-insensitively ({@code "3 BHK"} == {@code "3bhk"}),
 * keeping the first-seen spelling for display. Pure core has no Spring/DB dependency.
 */
@Service
@Transactional(readOnly = true)
public class BhkSupplyGapAnalyzer {

    private final ReconciliationService reconciliation;

    public BhkSupplyGapAnalyzer(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    /** Map BHK supply/gaps across a whole session (labels supplied by the caller). */
    public List<BhkSupplyGap> compare(UUID sessionId, Map<UUID, String> labels) {
        return analyze(reconciliation.consolidate(sessionId), labels);
    }

    // ── pure core (no Spring / DB) ───────────────────────────────────────────────

    public static List<BhkSupplyGap> analyze(List<ConsolidatedParameter> consolidated,
                                             Map<UUID, String> labels) {
        Map<UUID, Set<String>> configs = new LinkedHashMap<>(); // entity -> normalized config tokens
        for (ConsolidatedParameter c : consolidated) {
            if (c.parameter() == ParameterType.BHK_CONFIG) {
                Set<String> norm = new LinkedHashSet<>();
                for (String tok : AnalyticsValues.tokens(c.displayValue())) {
                    norm.add(normalize(tok));
                }
                if (!norm.isEmpty()) {
                    configs.put(c.entityId(), norm);
                }
            }
        }

        int marketSize = configs.size();
        if (marketSize < 2) {
            return List.of(); // need at least two projects with a BHK configuration to find gaps
        }

        // Union of configs across the market, normalized -> first-seen display spelling.
        Map<String, String> display = new LinkedHashMap<>();
        for (ConsolidatedParameter c : consolidated) {
            if (c.parameter() == ParameterType.BHK_CONFIG && configs.containsKey(c.entityId())) {
                for (String tok : AnalyticsValues.tokens(c.displayValue())) {
                    display.putIfAbsent(normalize(tok), tok.trim());
                }
            }
        }

        List<BhkSupplyGap> out = new ArrayList<>();
        display.forEach((norm, label) -> {
            List<String> offeredBy = new ArrayList<>();
            List<String> missingFrom = new ArrayList<>();
            configs.forEach((entityId, set) -> {
                String name = labels.getOrDefault(entityId, entityId.toString());
                (set.contains(norm) ? offeredBy : missingFrom).add(name);
            });
            out.add(new BhkSupplyGap(label, offeredBy.size(), marketSize, offeredBy, missingFrom));
        });
        out.sort(Comparator.comparingInt(BhkSupplyGap::supplyCount).thenComparing(BhkSupplyGap::configuration));
        return out;
    }

    private static String normalize(String token) {
        return token.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
