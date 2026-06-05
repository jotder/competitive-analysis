/**
 * Analytics: deterministic comparison engines (amenity premium, floor-rise, BHK-supply gap,
 * RERA-vs-listing discrepancy). Reads consolidated values + catalog. No dependency on collection or
 * intelligence — pure and unit-testable (ADR-0001).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "analytics",
        allowedDependencies = {"catalog", "reconciliation", "shared"})
package com.antigravity.companalysis.analytics;
