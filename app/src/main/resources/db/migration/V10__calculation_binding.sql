-- S3-T3 (E8-03b, ADR-009 mục 3, D-040 entity taxonomy v2 dependency):
-- Calculation Binding — how a DAG `query` node resolves a value from
-- Product/Customer (direct attribute read) or Extension/Parameter (filter
-- -> sort -> take-first pipeline). One calculation_binding row is looked
-- up by its attribute_code (the name the owning `query` node config
-- references it by, see QueryNodeExecutor) — unique so a node's reference
-- always resolves to exactly one binding definition.
CREATE TABLE calculation_binding (
    id                     BIGSERIAL PRIMARY KEY,
    attribute_code         VARCHAR(100) NOT NULL UNIQUE,
    source_type            VARCHAR(20)  NOT NULL CHECK (source_type IN ('PRODUCT', 'CUSTOMER', 'EXTENSION', 'PARAMETER')),
    source_entity_code     VARCHAR(100),
    source_attribute_code  VARCHAR(100) NOT NULL,
    take_first_after_sort  BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Filter conditions (0..N, AND-ed together) narrowing the candidate rows
-- of an EXTENSION/PARAMETER binding before sort/take-first (ADR-009 mục
-- 3). value_source = CONTEXT_ATTRIBUTE means `value` holds an *input
-- name* of the owning `query` DagNode (resolved from node.inputs() at
-- execution time, see QueryNodeExecutor) rather than a literal; REQUEST_PARAM
-- is declared for schema completeness but not implemented yet (NEEDS-PO-REVIEW,
-- see docs/decisions.md — no request-level param plumbing into the DAG
-- exists before S3-T5).
CREATE TABLE calculation_binding_filter (
    id            BIGSERIAL PRIMARY KEY,
    binding_id    BIGINT       NOT NULL REFERENCES calculation_binding (id),
    field_code    VARCHAR(100) NOT NULL,
    operator      VARCHAR(10)  NOT NULL CHECK (operator IN ('=', '!=', '<', '<=', '>', '>=', 'in')),
    value_source  VARCHAR(20)  NOT NULL CHECK (value_source IN ('CONSTANT', 'CONTEXT_ATTRIBUTE', 'REQUEST_PARAM')),
    value         TEXT
);

CREATE INDEX ix_calculation_binding_filter_binding_id ON calculation_binding_filter (binding_id);

-- Sort fields (0..N, ordered by sort_order), applied only when >=2 rows
-- remain after filtering (ADR-009 mục 3) — used together with
-- take_first_after_sort to resolve ambiguity deterministically, or to
-- surface it (CalculationBindingAmbiguousException) when the admin hasn't
-- opted into "take first".
CREATE TABLE calculation_binding_sort (
    id          BIGSERIAL PRIMARY KEY,
    binding_id  BIGINT       NOT NULL REFERENCES calculation_binding (id),
    field_code  VARCHAR(100) NOT NULL,
    direction   VARCHAR(4)   NOT NULL CHECK (direction IN ('ASC', 'DESC')),
    sort_order  INT          NOT NULL
);

CREATE INDEX ix_calculation_binding_sort_binding_id ON calculation_binding_sort (binding_id);
