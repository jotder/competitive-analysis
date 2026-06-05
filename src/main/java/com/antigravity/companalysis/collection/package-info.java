/**
 * Collection: targeted, selection-driven acquisition for the 1–4 chosen developers (RERA-first),
 * plus comparable-set gathering. Isolated and untrusted — writes observations to {@code catalog} only.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "collection",
        allowedDependencies = {"catalog", "shared"})
package com.antigravity.companalysis.collection;
