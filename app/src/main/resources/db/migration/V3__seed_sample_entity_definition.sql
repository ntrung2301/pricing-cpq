-- S1-T8 walking-skeleton seed data ONLY — this is NOT a real product
-- feature. It exists purely so GET /api/entities has something real in
-- Postgres to return for the manual/E2E "login -> see real data from
-- Postgres on the UI, no mock" proof (sprint-1.md S1-T8 Definition of
-- Done). The automated backend test (EntitiesEndpointIT) seeds its own
-- entity definitions through the service layer and does not depend on
-- this row.
--
-- "SampleWidget" deliberately follows the same generic/non-domain-specific
-- naming convention S1-T7's tests already used ("TestWidget") — not
-- automotive, not any real industry. This row (and migration) should be
-- superseded by real catalog import once Sprint 2's domain pack loading
-- (E1-01/ADR-001) lands; it is explicitly temporary.

INSERT INTO entity_def (code, label, version)
VALUES ('SampleWidget', 'Sample Widget', 1);

INSERT INTO attribute_def (entity_def_id, code, label, data_type, required, external_code_hint)
SELECT id, 'name', 'Name', 'STRING', TRUE, NULL
FROM entity_def WHERE code = 'SampleWidget';

INSERT INTO attribute_def (entity_def_id, code, label, data_type, required, external_code_hint)
SELECT id, 'quantity', 'Quantity', 'NUMBER', TRUE, NULL
FROM entity_def WHERE code = 'SampleWidget';

INSERT INTO attribute_def (entity_def_id, code, label, data_type, required, external_code_hint)
SELECT id, 'notes', 'Notes', 'STRING', FALSE, NULL
FROM entity_def WHERE code = 'SampleWidget';
