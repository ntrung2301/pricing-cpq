-- S2-T7 (ADR-003 "Supersession"/"Kit"): relationship_instance — one
-- concrete edge of a relationship_def between two entity_instance rows.
-- Core only knows generic "from"/"to" (no "supersedes"/"contains"/"part"
-- column names); a relationship_def of code "supersedes" uses this table
-- to mean "from_instance_id supersedes/replaces to_instance_id" (D-025
-- point 3: from = replacement, to = replaced); a relationship_def of code
-- "contains" uses it for kit/bundle composition instead — both are
-- domain-pack data (automotive's pack.yaml, S2-T3), not core concepts.
--
-- attributes is a nullable free-form JSON text column (e.g. for an
-- "effective_date" on a supersession) — same TEXT-not-native-jsonb choice
-- already made for constraint_instance.condition (V5): no JSON operators
-- are needed at query time, so a jsonb column/extra dependency isn't
-- justified yet.
CREATE TABLE relationship_instance (
    id                   BIGSERIAL PRIMARY KEY,
    relationship_def_id  BIGINT      NOT NULL REFERENCES relationship_def (id),
    from_instance_id     BIGINT      NOT NULL REFERENCES entity_instance (id),
    to_instance_id       BIGINT      NOT NULL REFERENCES entity_instance (id),
    attributes           TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_relationship_instance_relationship_def_id ON relationship_instance (relationship_def_id);
CREATE INDEX ix_relationship_instance_from_instance_id ON relationship_instance (from_instance_id);
CREATE INDEX ix_relationship_instance_to_instance_id ON relationship_instance (to_instance_id);
