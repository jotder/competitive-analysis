package com.antigravity.companalysis.intelligence.internal;

import java.util.Map;
import java.util.UUID;

import com.antigravity.companalysis.shared.ParameterType;

/**
 * Lenient parsing of the loosely-typed {@code args} map a kernel {@code Tool} receives: callers may pass
 * a typed value (a {@link UUID}, a {@link ParameterType}) or its string form, and absent/blank/invalid
 * entries resolve to {@code null} so a tool returns {@code noData} rather than throwing.
 */
final class ToolArgs {

    private ToolArgs() {
    }

    static UUID sessionId(Map<String, Object> args) {
        Object raw = (args == null) ? null : args.get("sessionId");
        if (raw instanceof UUID u) {
            return u;
        }
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static ParameterType parameter(Map<String, Object> args) {
        Object raw = (args == null) ? null : args.get("parameter");
        if (raw instanceof ParameterType p) {
            return p;
        }
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        try {
            return ParameterType.valueOf(raw.toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
