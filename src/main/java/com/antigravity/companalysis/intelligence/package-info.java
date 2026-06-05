/**
 * Intelligence: the LangChain4j + Gemini agent (ADR-0004). Orchestrates and narrates only; never
 * calculates (ADR-0001). Binds a fixed, audited tool set over analytics/reconciliation/catalog/userinput
 * and does RAG over pgvector. Never calls {@code collection} directly.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "intelligence",
        allowedDependencies = {"analytics", "reconciliation", "catalog", "userinput", "shared"})
package com.antigravity.companalysis.intelligence;
