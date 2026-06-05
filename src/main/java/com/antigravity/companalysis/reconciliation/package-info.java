/**
 * Reconciliation (ADR-0002): normalize observations to a canonical basis, consolidate by credibility
 * (ANCHOR / WEIGHTED / RANGE — never flat-average), and quantify the spread. Pure and deterministic.
 *
 * <p>Reads {@code catalog} observations; produces {@code ConsolidatedParameter}s.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "reconciliation",
        allowedDependencies = {"catalog", "shared"})
package com.antigravity.companalysis.reconciliation;
