-- S2-T10 (E3-01/E3-02, ADR-002/ADR-003/ADR-004/D-014): rule engine +
-- price list persistence. Reuses the generic metamodel (entity_def /
-- entity_instance) for price_list_item's optional instance/segment
-- targeting, per ADR-003's "Price list" decision — no domain keyword
-- anywhere in this schema.
--
-- price_list / price_list_item (ADR-003 §"Price list"):
CREATE TABLE price_list (
    id       BIGSERIAL PRIMARY KEY,
    code     VARCHAR(100) NOT NULL,
    name     VARCHAR(255) NOT NULL,
    currency VARCHAR(3)   NOT NULL,
    CONSTRAINT uq_price_list_code UNIQUE (code)
);

-- entity_instance_id is nullable: a NULL row applies by
-- condition/segment (e.g. "default price for this entity_def") rather
-- than to one specific instance (ADR-003).
CREATE TABLE price_list_item (
    id                 BIGSERIAL PRIMARY KEY,
    price_list_id      BIGINT        NOT NULL REFERENCES price_list (id),
    entity_def_id      BIGINT        NOT NULL REFERENCES entity_def (id),
    entity_instance_id BIGINT        REFERENCES entity_instance (id),
    amount             NUMERIC(19, 4) NOT NULL,
    valid_from         DATE,
    valid_to           DATE
);

CREATE INDEX ix_price_list_item_price_list_id ON price_list_item (price_list_id);
CREATE INDEX ix_price_list_item_entity_def_id ON price_list_item (entity_def_id);
CREATE INDEX ix_price_list_item_entity_instance_id ON price_list_item (entity_instance_id);

-- rule_set / rule (ADR-002/D-014). `stage` is one of the 6 fixed, generic
-- ADR-004 pipeline stages — not domain-specific, and not something a
-- pack can extend (ADR-004 Hệ quả). `rule_type` is DSL|SCRIPT (D-014
-- hybrid rule engine); `definition` holds the JSON DSL document or raw
-- Groovy source, as plain text data (CLAUDE.md mục 7: "rule lưu dạng dữ
-- liệu, không hard-code"), never a compiled Java class.
CREATE TABLE rule_set (
    id      BIGSERIAL PRIMARY KEY,
    code    VARCHAR(100) NOT NULL,
    version INTEGER      NOT NULL DEFAULT 1,
    CONSTRAINT uq_rule_set_code UNIQUE (code)
);

CREATE TABLE rule (
    id          BIGSERIAL PRIMARY KEY,
    rule_set_id BIGINT       NOT NULL REFERENCES rule_set (id),
    stage       VARCHAR(30)  NOT NULL,
    priority    INTEGER      NOT NULL DEFAULT 0,
    rule_type   VARCHAR(10)  NOT NULL,
    definition  TEXT         NOT NULL,
    CONSTRAINT ck_rule_stage CHECK (
        stage IN (
            'base', 'segment-adjustment', 'quantity-adjustment',
            'discount', 'override', 'guardrail'
        )
    ),
    CONSTRAINT ck_rule_type CHECK (rule_type IN ('DSL', 'SCRIPT'))
);

CREATE INDEX ix_rule_rule_set_id ON rule (rule_set_id);
