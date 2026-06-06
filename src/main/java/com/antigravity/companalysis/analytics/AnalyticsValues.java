package com.antigravity.companalysis.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.antigravity.companalysis.reconciliation.ConsolidatedParameter;

/**
 * Small deterministic helpers shared by the analytics engines (ADR-0001): how to read a single numeric
 * figure out of a {@link ConsolidatedParameter}, and how to split a non-numeric multi-value cell (amenity
 * list, BHK set) into tokens. Pure and Spring-free so every engine stays unit-testable.
 */
final class AnalyticsValues {

    private static final BigDecimal TWO = new BigDecimal("2");

    private AnalyticsValues() {
    }

    /** The numeric figure for an engine to compare: the consolidated point, or a RANGE band's midpoint. */
    static BigDecimal numeric(ConsolidatedParameter c) {
        if (c.value() != null) {
            return c.value();
        }
        if (c.low() != null && c.high() != null) {
            return c.low().add(c.high()).divide(TWO, 4, RoundingMode.HALF_UP);
        }
        return null;
    }

    /** Split a multi-value display cell ("Pool, Gym; Clubhouse" / "2BHK | 3BHK") into trimmed, distinct tokens. */
    static List<String> tokens(String display) {
        if (display == null || display.isBlank()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String part : display.split("[,;|/]+")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                seen.add(t);
            }
        }
        return new ArrayList<>(seen);
    }
}
