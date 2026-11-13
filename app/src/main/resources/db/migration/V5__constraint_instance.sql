-- S2-T2 (E1-02, ADR-001/ADR-002): constraint_instance — one concrete
-- assertion of a constraint_def between two entity_instance rows, holding
-- the JSON condition to evaluate. Core only knows generic "x"/"y" sides
-- (no "part"/"vehicle" column names); a constraint_def of type
-- "compatibility" uses this table to mean "entity_instance_x_id is
-- compatible with entity_instance_y_id if condition holds" — evaluated by
-- ConstraintEngine (core-constraint-engine), not by this table.
--
-- pack_id/pack_version are nullable here (unlike entity_instance, which
-- requires NOT NULL) because S2-T2's scope deliberately does not add
-- constraint_instances[] to PackLoaderService/pack.yaml yet (see
-- docs/decisions.md D-021) — rows created directly through the service
-- layer/tests have no pack of origin, same reasoning as the nullable
-- pack_id/pack_version on the 4 *definition* tables (D-020).
CREATE TABLE constraint_instance (
    id                   BIGSERIAL PRIMARY KEY,
    constraint_def_id    BIGINT       NOT NULL REFERENCES constraint_def (id),
    condition            TEXT         NOT NULL,
    entity_instance_x_id BIGINT       NOT NULL REFERENCES entity_instance (id),
    entity_instance_y_id BIGINT       NOT NULL REFERENCES entity_instance (id),
    pack_id              VARCHAR(100),
    pack_version         INTEGER,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_constraint_instance_constraint_def_id ON constraint_instance (constraint_def_id);
CREATE INDEX ix_constraint_instance_entity_instance_x_id ON constraint_instance (entity_instance_x_id);
CREATE INDEX ix_constraint_instance_entity_instance_y_id ON constraint_instance (entity_instance_y_id);
