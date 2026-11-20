-- S3-T1 (E8-01, ADR-009 mục 1 amending ADR-001, D-037/D-038/D-039):
-- entity taxonomy v2 — classifies every entity_def as ANCHOR (exactly
-- Product/Customer, created only by CoreAnchorBootstrapService, never by a
-- pack), EXTENSION (extends exactly one other entity_def — a
-- generalisation of ADR-009's "ProductExtension"/"CustomerExtension"
-- categories, e.g. CostHistory extending Product), or PARAMETER (an
-- independent entity with its own key, e.g. Vehicle, MarginThreshold).
--
-- role is nullable: a pack is not required to declare it (D-039,
-- PackLoaderService "giữ nguyên hành vi cũ" rule) and this column is new
-- on an already-populated table (Sprint 1/2 demo data).
ALTER TABLE entity_def
    ADD COLUMN role VARCHAR(20) CHECK (role IN ('ANCHOR', 'EXTENSION', 'PARAMETER')),
    ADD COLUMN extends_entity_def_id BIGINT REFERENCES entity_def (id);

-- Backfill already-seeded Sprint 1/2 data so nothing is left untagged:
-- Product/Customer (automotive pack, S2-T3/S2-T4, if this deployment ever
-- ran that pack) become the 2 fixed anchors; every other entity_def that
-- already exists (Vehicle, and Sprint 1's TestWidget/SampleWidget if still
-- present) becomes PARAMETER — none of Sprint 1/2's entity defs is an
-- EXTENSION (no existing entity_def has a composite anchor-derived key).
-- On a brand-new deployment (no entity_def rows yet) these are no-ops;
-- CoreAnchorBootstrapService (app) creates Product/Customer with
-- role = 'ANCHOR' at first startup instead.
UPDATE entity_def SET role = 'ANCHOR' WHERE code IN ('Product', 'Customer');
UPDATE entity_def SET role = 'PARAMETER' WHERE role IS NULL;

-- entity_def_dimension_attribute: ordered list of an EXTENSION entity_def's
-- "dimension" attributes (e.g. period, city) — together with the extended
-- instance's own business_key, these assemble an EXTENSION instance's
-- composite business_key (D-039: "<anchor businessKey>|<dim1>|<dim2>...").
-- position orders multiple dimensions deterministically.
CREATE TABLE entity_def_dimension_attribute (
    id               BIGSERIAL PRIMARY KEY,
    entity_def_id    BIGINT  NOT NULL REFERENCES entity_def (id),
    attribute_def_id BIGINT  NOT NULL REFERENCES attribute_def (id),
    position         INTEGER NOT NULL,
    CONSTRAINT uq_edda_entity_def_attribute UNIQUE (entity_def_id, attribute_def_id),
    CONSTRAINT uq_edda_entity_def_position UNIQUE (entity_def_id, position)
);

CREATE INDEX ix_edda_entity_def_id ON entity_def_dimension_attribute (entity_def_id);

-- entity_instance.anchor_instance_id: only set when the instance's
-- entity_def has role = 'EXTENSION' — points at the entity_instance (of
-- entity_def.extends_entity_def_id) it extends. Self-referencing FK, same
-- pattern already used for supersession in relationship_instance (V8).
ALTER TABLE entity_instance
    ADD COLUMN anchor_instance_id BIGINT REFERENCES entity_instance (id);

CREATE INDEX ix_entity_instance_anchor_instance_id ON entity_instance (anchor_instance_id);
