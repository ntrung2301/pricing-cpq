-- S3-T6 (E8-05, ADR-009 mục 5): Price List module built on the DAG engine
-- (S3-T2/T3/T5) — a parallel, independent schema to the Sprint 2
-- RuleEngine-based `price_list`/`price_list_item` (V7__pricing_rule_engine.sql),
-- which stays untouched per sprint-3.md's "NEEDS-PO-REVIEW đang mở" note
-- (no migration decision made yet). Named with a `_v2` suffix (table AND
-- Java entity class, see docs/decisions.md D-046) to make the two engines
-- unmistakably distinct rather than silently reusing/overloading a name
-- already in use.
--
-- `calculation_graph` is a `List<DagNode>` stored verbatim as `jsonb`
-- (same shape/converter as `measure_definition.graph`, V11); `result_node_id`
-- names which of those nodes' output becomes the Price List item's
-- `result_price` when the graph is run per-Product (see
-- com.cpq.app.pricing.pricelist.PriceListServiceImpl.addItem). `version`
-- is part of the schema per ADR-009 mục 5 ("approve xong khoá, sửa tiếp
-- tạo version mới") but no S3-T6 endpoint bumps it yet — there is no "edit
-- calculation_graph after creation" endpoint in this task's scope, so it
-- is always 1 for now (NEEDS-PO-REVIEW, see docs/decisions.md D-046).
CREATE TABLE price_list_v2 (
    id                 BIGSERIAL PRIMARY KEY,
    code               VARCHAR(100) NOT NULL UNIQUE,
    name               VARCHAR(255) NOT NULL,
    currency           VARCHAR(10)  NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                           CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'ACTIVE', 'EXPIRED', 'REJECTED')),
    valid_from         DATE,
    valid_to           DATE,
    calculation_graph  JSONB        NOT NULL,
    result_node_id     VARCHAR(100) NOT NULL,
    version            INT          NOT NULL DEFAULT 1,
    created_by         VARCHAR(100),
    submitted_at       TIMESTAMP,
    approved_by        VARCHAR(100),
    approved_at        TIMESTAMP
);

-- One Product (entity_instance) priced within a price_list_v2.
-- `result_price` is computed exactly once (when the item is added, by
-- running calculation_graph with this row's entity_instance_id baked in
-- as the DAG's anchor) and is a permanent snapshot once the parent
-- price_list_v2 is APPROVED (ADR-009 mục 5) — no column here is meant to
-- be edited directly except `manual_override_price`, enforced at the
-- application layer (no PATCH/PUT endpoint touches any other column).
-- `waterfall_steps` holds the full Calculation Trace inline as `jsonb`,
-- deliberately not split into a child table (ADR-009 mục 5: "KHÔNG tách
-- bảng con riêng").
CREATE TABLE price_list_item_v2 (
    id                     BIGSERIAL PRIMARY KEY,
    price_list_id          BIGINT         NOT NULL REFERENCES price_list_v2 (id),
    entity_instance_id     BIGINT         NOT NULL REFERENCES entity_instance (id),
    result_price           NUMERIC(19, 4),
    manual_override_price  NUMERIC(19, 4),
    waterfall_steps        JSONB,
    valid_from             DATE,
    valid_to               DATE
);

CREATE INDEX ix_price_list_item_v2_price_list_id ON price_list_item_v2 (price_list_id);
CREATE INDEX ix_price_list_item_v2_entity_instance_id ON price_list_item_v2 (entity_instance_id);
