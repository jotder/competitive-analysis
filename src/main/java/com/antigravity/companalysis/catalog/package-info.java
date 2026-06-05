/**
 * Catalog: the canonical, provenance-tagged model — developer entities, append-only
 * {@code ParameterObservation}s, and comparison sessions. The shared contract every module agrees on.
 *
 * <p>Depends only on the open {@code shared} module.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "catalog",
        allowedDependencies = {"shared"})
package com.antigravity.companalysis.catalog;
