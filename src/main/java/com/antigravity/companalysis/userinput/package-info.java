/**
 * User-input: gap-fill (USER_PROVIDED observations), what-if scenarios (ASSUMPTION observations),
 * and the optional "our project" anchor. Writes observations to {@code catalog} only.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "userinput",
        allowedDependencies = {"catalog", "shared"})
package com.antigravity.companalysis.userinput;
