-- V2: RAG corpus. doc_chunk holds embedded passages from source documents (brochures, RERA filings,
-- listings) for QUALITATIVE grounding only — the agent cites a passage, it never extracts a figure from
-- one (ADR-0001; "RAG never supplies numbers"). Read at query time by the kernel's pgvector Retriever
-- (agent-store-postgres) over the default doc_chunk(content, source_ref, embedding) shape.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE doc_chunk (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id   UUID REFERENCES developer_entity(id),  -- optional: the project this passage is about
    source_ref  VARCHAR(2048) NOT NULL,                -- citation/locator (never the value itself; ADR-0008)
    content     TEXT          NOT NULL,                -- the passage text
    embedding   vector(768),                           -- text-embedding-004 dimensionality
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Approximate nearest-neighbour index for cosine distance (the <=> operator the Retriever orders by).
CREATE INDEX idx_doc_chunk_embedding ON doc_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_doc_chunk_entity    ON doc_chunk (entity_id);
