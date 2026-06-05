package com.antigravity.companalysis.shared;

/**
 * The area definition a value is quoted against. RERA uses CARPET (legally mandated); portals/brochures
 * often quote SUPER_BUILTUP (inflated). Reconciliation normalizes everything to carpet area (ADR-0002).
 */
public enum AreaBasis {
    CARPET,
    BUILTUP,
    SUPER_BUILTUP
}
