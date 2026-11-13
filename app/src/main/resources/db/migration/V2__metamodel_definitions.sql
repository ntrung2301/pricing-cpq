-- S1-T7: generic metamodel *definition* tables (ADR-001/ADR-003). Core only
-- knows these 4 concepts and has no "part"/"vehicle"/"fitment" table — a
-- domain pack's entities/attributes/relationships/constraints are rows
-- here, loaded from pack.yaml (see docs/adr/0001-metamodel-and-domain-pack.md).
--
-- Deliberately NOT in scope here: the entity_instance /
-- entity_instance_attribute_value typed-EAV runtime tables — those are
-- Sprint 2 (docs/roadmap.md), tracked as E1-01 in docs/backlog.md.

CREATE TABLE entity_def (
    id      BIGSERIAL PRIMARY KEY,
    code    VARCHAR(100) NOT NULL,
    label   VARCHAR(255) NOT NULL,
    version INTEGER      NOT NULL DEFAULT 1,
    CONSTRAINT uq_entity_def_code UNIQUE (code)
);

CREATE TABLE attribute_def (
    id                  BIGSERIAL PRIMARY KEY,
    entity_def_id       BIGINT       NOT NULL REFERENCES entity_def (id),
    code                VARCHAR(100) NOT NULL,
    label               VARCHAR(255) NOT NULL,
    data_type           VARCHAR(20)  NOT NULL,
    required            BOOLEAN      NOT NULL DEFAULT FALSE,
    external_code_hint  TEXT,
    CONSTRAINT uq_attribute_def_entity_code UNIQUE (entity_def_id, code),
    CONSTRAINT ck_attribute_def_data_type CHECK (
        data_type IN (
            'STRING', 'NUMBER', 'DECIMAL', 'BOOLEAN', 'DATE', 'ENUM',
            'REFERENCE', 'JSON'
        )
    )
);

CREATE INDEX ix_attribute_def_entity_def_id ON attribute_def (entity_def_id);

CREATE TABLE relationship_def (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(100) NOT NULL,
    from_entity_def_id  BIGINT       NOT NULL REFERENCES entity_def (id),
    to_entity_def_id    BIGINT       NOT NULL REFERENCES entity_def (id),
    cardinality         VARCHAR(20)  NOT NULL,
    CONSTRAINT uq_relationship_def_code UNIQUE (code),
    CONSTRAINT ck_relationship_def_cardinality CHECK (
        cardinality IN ('ONE_TO_ONE', 'ONE_TO_MANY', 'MANY_TO_MANY')
    )
);

CREATE INDEX ix_relationship_def_from_entity_def_id ON relationship_def (from_entity_def_id);
CREATE INDEX ix_relationship_def_to_entity_def_id ON relationship_def (to_entity_def_id);

CREATE TABLE constraint_def (
    id      BIGSERIAL PRIMARY KEY,
    code    VARCHAR(100) NOT NULL,
    type    VARCHAR(50)  NOT NULL,
    CONSTRAINT uq_constraint_def_code UNIQUE (code)
);
