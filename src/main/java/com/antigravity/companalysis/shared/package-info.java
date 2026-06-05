/**
 * Shared kernel: common types, provenance/credibility-tier enums, events.
 *
 * <p>Open module — any module may depend on it (it carries no business logic that needs hiding).
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "shared")
package com.antigravity.companalysis.shared;
