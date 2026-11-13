-- S2-T1 (E1-01, ADR-001 amended by D-019): adds the typed-EAV runtime
-- tables that were deliberately left out of V2 (entity_instance /
-- entity_instance_attribute_value), plus pack provenance tracking
-- (pack_id/pack_version) on all 4 *definition* tables, plus the generic
-- key/label attribute pointers on entity_def and their denormalized
-- counterparts on entity_instance (D-019).
--
-- pack_id/pack_version are nullable on the 4 definition tables: rows
-- created directly through the service layer (not via a pack.yaml), e.g.
-- the existing S1-T7/S1-T8 "TestWidget"/"SampleWidget" definitions, have
-- no pack of origin. PackLoaderService always sets both when it upserts a
-- definition from pack.yaml (ADR-001).

ALTER TABLE entity_def
    ADD COLUMN pack_id      VARCHAR(100),
    ADD COLUMN pack_version INTEGER,
    ADD COLUMN key_attribute_def_id   BIGINT REFERENCES attribute_def (id),
    ADD COLUMN label_attribute_def_id BIGINT REFERENCES attribute_def (id);

ALTER TABLE attribute_def
    ADD COLUMN pack_id      VARCHAR(100),
    ADD COLUMN pack_version INTEGER;

ALTER TABLE relationship_def
    ADD COLUMN pack_id      VARCHAR(100),
    ADD COLUMN pack_version INTEGER;

ALTER TABLE constraint_def
    ADD COLUMN pack_id      VARCHAR(100),
    ADD COLUMN pack_version INTEGER;

-- entity_instance: one row per instance of an entity_def. pack_id/
-- pack_version are NOT NULL here (unlike the definition tables above) —
-- every instance is created in the context of a specific pack (even a
-- synthetic/test one in tests), there is no "manually created, no pack"
-- case for instance data the way there is for S1-T7-era definitions.
--
-- business_key/display_label (D-019) are denormalized copies of the value
-- held in entity_instance_attribute_value for entity_def's
-- key_attribute_def_id/label_attribute_def_id, kept in sync by the
-- service layer (EntityInstanceService) whenever that attribute's value
-- is written — not a DB trigger, so the sync logic stays in Java where
-- it's unit/integration-testable and auditable (per task scope).
CREATE TABLE entity_instance (
    id            BIGSERIAL PRIMARY KEY,
    entity_def_id BIGINT       NOT NULL REFERENCES entity_def (id),
    pack_id       VARCHAR(100) NOT NULL,
    pack_version  INTEGER      NOT NULL,
    business_key   VARCHAR(255),
    display_label  VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_entity_instance_entity_def_id ON entity_instance (entity_def_id);

-- Unique business key per (entity_def, pack) — D-019. Postgres unique
-- indexes treat multiple NULLs as distinct, so entity_instances of an
-- entity_def with no key_attribute_def_id (business_key always NULL) are
-- simply not key-constrained, which is the correct generic behaviour.
CREATE UNIQUE INDEX uq_entity_instance_def_pack_business_key
    ON entity_instance (entity_def_id, pack_id, business_key);

-- entity_instance_attribute_value: typed EAV (ADR-001) — exactly one of
-- the value_* columns is populated, matching attribute_def.data_type
-- (validated by the service layer when writing, not by this table alone).
-- One row per (entity_instance, attribute_def) — single-valued attributes
-- only for S2-T1 scope; multi-valued attributes are not in scope.
CREATE TABLE entity_instance_attribute_value (
    id                   BIGSERIAL PRIMARY KEY,
    entity_instance_id   BIGINT NOT NULL REFERENCES entity_instance (id) ON DELETE CASCADE,
    attribute_def_id     BIGINT NOT NULL REFERENCES attribute_def (id),
    value_string         TEXT,
    value_number         NUMERIC(20, 6),
    value_date           DATE,
    value_boolean        BOOLEAN,
    value_ref_instance_id BIGINT REFERENCES entity_instance (id),
    CONSTRAINT uq_eiav_instance_attribute UNIQUE (entity_instance_id, attribute_def_id),
    CONSTRAINT ck_eiav_single_typed_value CHECK (
        (CASE WHEN value_string IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN value_number IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN value_date IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN value_boolean IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN value_ref_instance_id IS NOT NULL THEN 1 ELSE 0 END) <= 1
    )
);

CREATE INDEX ix_eiav_entity_instance_id ON entity_instance_attribute_value (entity_instance_id);
CREATE INDEX ix_eiav_attribute_def_id ON entity_instance_attribute_value (attribute_def_id);
