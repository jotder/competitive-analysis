-- V1: core "trust machinery" schema (architecture_v2_prudent.md §5).
-- Establishes bounded comparison sessions, developer entities, append-only parameter
-- observations (one row per source value — never overwritten), and reconciled values.
-- Analytics metrics (computed_metric) and RAG (doc_chunk + pgvector) arrive in later migrations.

CREATE TABLE comparison_session (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title               VARCHAR(255) NOT NULL,
    micro_market        VARCHAR(255),
    selected_entity_ids JSONB        NOT NULL DEFAULT '[]'::jsonb,  -- 1..4 entity ids
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE developer_entity (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    developer_name          VARCHAR(255) NOT NULL,
    project_name            VARCHAR(255),
    micro_market_area       VARCHAR(255),
    rera_registration_number VARCHAR(100),
    is_ours                 BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Append-only. The three different source values for one parameter are three rows.
CREATE TABLE parameter_observation (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id        UUID         NOT NULL REFERENCES developer_entity(id),
    parameter        VARCHAR(64)  NOT NULL,  -- PRICE | CARPET_AREA | POSSESSION_DATE | AMENITY ...
    raw_value        VARCHAR(512),
    raw_unit         VARCHAR(64),
    area_basis       VARCHAR(32),            -- CARPET | BUILTUP | SUPER_BUILTUP
    normalized_value NUMERIC(18, 4),
    normalized_unit  VARCHAR(64),            -- e.g. INR_PER_CARPET_SQFT
    source           VARCHAR(64)  NOT NULL,  -- RERA | DEVELOPER_SITE | MAGICBRICKS | USER | ASSUMPTION
    tier             VARCHAR(32)  NOT NULL,  -- AUTHORITATIVE | INDICATIVE | USER_PROVIDED | ASSUMPTION
    confidence       NUMERIC(4, 3),          -- 0..1
    source_ref       VARCHAR(2048),
    scenario_id      UUID,                   -- non-null only for ASSUMPTION (what-if) observations
    observed_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_observation_entity_param ON parameter_observation (entity_id, parameter);
CREATE INDEX idx_observation_scenario     ON parameter_observation (scenario_id);

-- Result of reconciliation (ADR-0002): never a flat average; anchors on credibility and keeps the spread.
CREATE TABLE consolidated_parameter (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id     UUID         NOT NULL REFERENCES developer_entity(id),
    parameter     VARCHAR(64)  NOT NULL,
    method        VARCHAR(16)  NOT NULL,    -- ANCHOR | WEIGHTED | MEDIAN | RANGE
    value         NUMERIC(18, 4),
    range_low     NUMERIC(18, 4),
    range_high    NUMERIC(18, 4),
    anchor_source VARCHAR(64),
    variance_pct  NUMERIC(8, 4),
    agreement     VARCHAR(16),              -- HIGH | MODERATE | LOW
    inputs_ref    JSONB        NOT NULL DEFAULT '[]'::jsonb,  -- observation ids reconciled
    scenario_id   UUID,                     -- non-null = a what-if recomputation
    computed_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_consolidated_entity_param ON consolidated_parameter (entity_id, parameter);
