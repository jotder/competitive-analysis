package com.antigravity.companalysis;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * The "one boundary test" of the walking skeleton (ADR-0003).
 *
 * <p>{@code verify()} fails the build if any module reaches into another module's internals or
 * violates the allowed-dependency rules declared in each {@code package-info.java}. This is a pure
 * static analysis — no Spring context, no database, no Docker required.
 */
class ModularityTests {

    static final ApplicationModules modules = ApplicationModules.of(CompAnalysisApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        // Generates C4/PlantUML component diagrams + a module canvas under target/spring-modulith-docs.
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}
