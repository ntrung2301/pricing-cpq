-- S3-T5 (E8-04, ADR-009 mục 2 "Measure"): a named, reusable DAG subgraph,
-- defined once, referenced from many top-level graphs/Measures-of-other-
-- Measures-later via a `measure-ref` DagNode, parameterized at call time by
-- whichever anchor instance is currently being calculated (never a
-- hardcoded instance baked into the definition itself). `graph` is the
-- subgraph's `DagNode` list, stored verbatim as JSON (see
-- com.cpq.core.pricing.persistence.DagNodeGraphConverter); `code` is this
-- measure's lookup key for `measure-ref` nodes (config `measureCode`) —
-- same "lookup by portable business code, not surrogate id" convention as
-- calculation_binding.attribute_code (V10).
CREATE TABLE measure_definition (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    graph           JSONB        NOT NULL,
    result_node_id  VARCHAR(100) NOT NULL
);
