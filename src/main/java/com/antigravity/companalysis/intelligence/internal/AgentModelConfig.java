package com.antigravity.companalysis.intelligence.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gamma.agentkernel.model.ModelRouter;
import com.gamma.agentkernel.provider.langchain4j.GeminiModelProvider;

/**
 * Wires the agent's model backend: a {@link ModelRouter} over Google AI Gemini (R1.3; ADR-0011). This
 * overrides the kernel's abstain-safe default router (provided by {@code agent-kernel-spring} as
 * {@code @ConditionalOnMissingBean}). It is itself abstain-safe — {@link GeminiModelProvider#fromEnvironment()}
 * reads {@code GEMINI_API_KEY}/{@code agentkernel.gemini.*}, and with no key every tier resolves to an
 * unavailable provider, so capabilities narrate deterministically and no network call is made (CI, local).
 */
@Configuration
class AgentModelConfig {

    @Bean
    ModelRouter agentModelRouter() {
        return GeminiModelProvider.fromEnvironment();
    }
}
