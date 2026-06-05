/**
 * API: REST + SSE endpoints and authorization. The only module exposed to clients. Delegates to
 * intelligence/analytics/reconciliation/catalog/userinput.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "api",
        allowedDependencies = {"intelligence", "analytics", "reconciliation", "catalog", "userinput", "shared"})
package com.antigravity.companalysis.api;
