package com.antigravity.companalysis.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * The amenity-premium engine: across a session's projects, it pairs each project's consolidated
 * {@link ParameterType#PRICE} with its {@link ParameterType#AMENITIES} set and measures the price premium
 * over the cheapest comparable project, naming the amenities that premium buys. This is a competitive
 * "is the extra spend justified?" surface — the engine states the facts (premium %, the extra amenities);
 * the agent narrates the judgement, never the arithmetic (ADR-0001).
 *
 * <p>Needs ≥2 projects that each have <em>both</em> a numeric price and a non-empty amenity list; otherwise
 * there is nothing to compare and {@link #analyze} returns empty (the tool then abstains). Pure core has no
 * Spring/DB dependency.
 */
@Service
@Transactional(readOnly = true)
public class AmenityPremiumAnalyzer {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ReconciliationService reconciliation;

    public AmenityPremiumAnalyzer(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    /** Compare amenity-vs-price across a whole session (labels supplied by the caller). */
    public List<AmenityPremium> compare(UUID sessionId, Map<UUID, String> labels) {
        return analyze(reconciliation.consolidate(sessionId), labels);
    }

    // ── pure core (no Spring / DB) ───────────────────────────────────────────────

    public static List<AmenityPremium> analyze(List<ConsolidatedParameter> consolidated,
                                               Map<UUID, String> labels) {
        Map<UUID, BigDecimal> price = new LinkedHashMap<>();
        Map<UUID, String> priceUnit = new LinkedHashMap<>();
        Map<UUID, List<String>> amenities = new LinkedHashMap<>();
        for (ConsolidatedParameter c : consolidated) {
            if (c.parameter() == ParameterType.PRICE) {
                BigDecimal v = AnalyticsValues.numeric(c);
                if (v != null && v.signum() > 0) {
                    price.put(c.entityId(), v);
                    priceUnit.put(c.entityId(), c.unit());
                }
            } else if (c.parameter() == ParameterType.AMENITIES) {
                List<String> a = AnalyticsValues.tokens(c.displayValue());
                if (!a.isEmpty()) {
                    amenities.put(c.entityId(), a);
                }
            }
        }

        List<UUID> comparable = price.keySet().stream().filter(amenities::containsKey).toList();
        if (comparable.size() < 2) {
            return List.of(); // need at least two priced, amenity-bearing projects to compare
        }

        UUID baseline = comparable.stream().min(Comparator.comparing(price::get)).orElseThrow();
        BigDecimal basePrice = price.get(baseline);
        Set<String> baseAmenities = lowerSet(amenities.get(baseline));

        List<AmenityPremium> out = new ArrayList<>();
        for (UUID id : comparable) {
            BigDecimal p = price.get(id);
            List<String> a = sorted(amenities.get(id));
            boolean isBaseline = id.equals(baseline);
            BigDecimal premiumPct = isBaseline
                    ? BigDecimal.ZERO.setScale(2)
                    : p.subtract(basePrice).divide(basePrice, 6, RoundingMode.HALF_UP)
                            .multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            List<String> extra = isBaseline
                    ? List.of()
                    : a.stream().filter(x -> !baseAmenities.contains(x.toLowerCase(Locale.ROOT))).toList();
            out.add(new AmenityPremium(id, labels.getOrDefault(id, id.toString()), p, priceUnit.get(id),
                    a.size(), a, isBaseline, premiumPct, extra));
        }
        out.sort(Comparator.comparing(AmenityPremium::price));
        return out;
    }

    private static Set<String> lowerSet(List<String> tokens) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : tokens) {
            out.add(t.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static List<String> sorted(List<String> tokens) {
        return tokens.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
