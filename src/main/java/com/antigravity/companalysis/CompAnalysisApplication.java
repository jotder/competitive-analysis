package com.antigravity.companalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Real Estate CxO Decision-Support Agent.
 *
 * <p>Modular monolith (ADR-0003). Each direct sub-package of this package is a Spring Modulith
 * application module; their allowed dependencies are declared in each module's {@code package-info.java}
 * and verified by {@code ModularityTests}.
 */
@SpringBootApplication
public class CompAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompAnalysisApplication.class, args);
    }
}
