package com.antigravity.companalysis.analytics;

import java.util.List;

import com.antigravity.companalysis.shared.ParameterType;

/**
 * Market supply for one BHK configuration across a session's projects: how many of the compared projects
 * offer it, who offers it, and who does not. Deterministically computed by {@link BhkSupplyGapAnalyzer}
 * from each project's {@link ParameterType#BHK_CONFIG} set. A low {@code supplyCount} relative to
 * {@code marketSize} is the "gap" — a configuration few competitors cover (an opportunity, or a segment to
 * avoid). The agent narrates the implication; the engine only counts (ADR-0001).
 *
 * @param configuration the BHK config token (e.g. {@code 2BHK}, {@code 3BHK})
 * @param supplyCount   how many compared projects offer it
 * @param marketSize    how many projects were comparable (carried a BHK configuration at all)
 * @param offeredBy     labels of the projects that offer it
 * @param missingFrom   labels of the comparable projects that do not
 */
public record BhkSupplyGap(String configuration, int supplyCount, int marketSize,
                           List<String> offeredBy, List<String> missingFrom) {

    public BhkSupplyGap {
        offeredBy = (offeredBy == null) ? List.of() : List.copyOf(offeredBy);
        missingFrom = (missingFrom == null) ? List.of() : List.copyOf(missingFrom);
    }

    /** True when at least one comparable project does not offer this configuration. */
    public boolean isGap() {
        return !missingFrom.isEmpty();
    }
}
