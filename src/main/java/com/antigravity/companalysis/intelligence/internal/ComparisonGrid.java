package com.antigravity.companalysis.intelligence.internal;

import com.antigravity.companalysis.shared.ParameterType;

import java.util.List;
import java.util.UUID;

/**
 * The compact, deterministic comparison grid a {@link ComparisonGridTool} computes for the agent: one
 * {@link Cell} per (entity, parameter) holding the most-credible observed value and its provenance.
 * This is a tool <em>result value</em> — deterministic Java, never the LLM (ADR-0001). Each cell keeps
 * the credibility-tier label so the narration can state it on every figure (CxO's "every number carries
 * its tier" rule).
 */
record ComparisonGrid(String title, String microMarket, List<Cell> cells) {

    /**
     * One cell: the chosen value for {@code parameter} on {@code entityLabel}, with the CxO tier label it
     * came from and a source locator (never the value itself — ADR-0008).
     */
    record Cell(UUID entityId, String entityLabel, ParameterType parameter, String value, String unit,
                String tierLabel, String sourceRef) {
    }
}
