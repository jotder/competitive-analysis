package com.antigravity.companalysis.intelligence.internal;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gamma.agentkernel.retrieve.Retriever;
import com.gamma.agentkernel.store.postgres.EmbeddingModel;
import com.gamma.agentkernel.store.postgres.PgVectorRetriever;

/**
 * Wires the agent's RAG backend: a {@link Retriever} over the {@code doc_chunk} pgvector table (R1.4;
 * ADR-0013), supplying qualitative document grounding only — the agent cites a passage, never extracts a
 * figure from one (ADR-0001).
 *
 * <p><b>Off by default.</b> The bean is created only when {@code companalysis.rag.enabled=true}; with the
 * property unset the kernel's abstain-safe default {@code Retriever.NONE} (from {@code agent-kernel-spring})
 * applies and the {@code search-documents} capability simply reports no context. Even when enabled it is
 * abstain-safe: with no {@link EmbeddingModel} bean (an application supplies one, e.g. a LangChain4j
 * embedding model) it falls back to {@link Retriever#NONE} rather than fail to start.
 */
@Configuration
class RagConfig {

    @Bean
    @ConditionalOnProperty(name = "companalysis.rag.enabled", havingValue = "true")
    Retriever documentRetriever(DataSource dataSource, ObjectProvider<EmbeddingModel> embedders) {
        EmbeddingModel embedder = embedders.getIfAvailable();
        return (embedder == null) ? Retriever.NONE : PgVectorRetriever.create(dataSource, embedder);
    }
}
