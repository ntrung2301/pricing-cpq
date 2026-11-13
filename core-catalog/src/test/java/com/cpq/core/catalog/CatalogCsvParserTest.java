package com.cpq.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CatalogCsvParser} (S2-T5, E1-04): pure CSV
 * header/row parsing, no Spring/JPA/Testcontainers needed — the
 * header-to-{@code attribute_def.code} matching (D-005) and row-level
 * syntax checks are plain Java logic, independent of how the matched rows
 * eventually get persisted (that orchestration lives in {@code app}).
 */
class CatalogCsvParserTest {

    private static final List<ImportColumnSpec> SKU_AND_NAME =
            List.of(new ImportColumnSpec("sku", true), new ImportColumnSpec("name", true));

    @Test
    void parsesAllRowsWhenHeaderMatchesExactly() {
        String csv = "sku,name\nSKU-1,Widget One\nSKU-2,Widget Two\n";

        ParsedCatalogCsv parsed = CatalogCsvParser.parse(SKU_AND_NAME, csv);

        assertThat(parsed.isHeaderValid()).isTrue();
        assertThat(parsed.headerErrors()).isEmpty();
        assertThat(parsed.rowErrors()).isEmpty();
        assertThat(parsed.rows()).hasSize(2);
        assertThat(parsed.rows().get(0).rowNumber()).isEqualTo(1);
        assertThat(parsed.rows().get(0).valuesByColumnCode()).containsEntry("sku", "SKU-1");
        assertThat(parsed.rows().get(0).valuesByColumnCode()).containsEntry("name", "Widget One");
        assertThat(parsed.rows().get(1).rowNumber()).isEqualTo(2);
        assertThat(parsed.rows().get(1).valuesByColumnCode()).containsEntry("sku", "SKU-2");
    }

    @Test
    void unknownHeaderColumnIsReportedAsHeaderErrorAtRowZero() {
        String csv = "sku,nmae\nSKU-1,Widget One\n";

        ParsedCatalogCsv parsed = CatalogCsvParser.parse(SKU_AND_NAME, csv);

        assertThat(parsed.isHeaderValid()).isFalse();
        assertThat(parsed.rows()).isEmpty();
        assertThat(parsed.headerErrors())
                .anySatisfy(
                        error -> {
                            assertThat(error.rowNumber()).isEqualTo(0);
                            assertThat(error.columnName()).isEqualTo("nmae");
                        });
    }

    @Test
    void missingRequiredColumnIsReportedAsHeaderErrorAtRowZero() {
        String csv = "sku\nSKU-1\n"; // "name" column (required) entirely absent

        ParsedCatalogCsv parsed = CatalogCsvParser.parse(SKU_AND_NAME, csv);

        assertThat(parsed.isHeaderValid()).isFalse();
        assertThat(parsed.rows()).isEmpty();
        assertThat(parsed.headerErrors())
                .anySatisfy(
                        error -> {
                            assertThat(error.rowNumber()).isEqualTo(0);
                            assertThat(error.columnName()).isEqualTo("name");
                        });
    }

    @Test
    void rowWithWrongFieldCountIsReportedButOtherRowsStillParse() {
        String csv = "sku,name\nSKU-1,Widget One\nSKU-2\nSKU-3,Widget Three\n";

        ParsedCatalogCsv parsed = CatalogCsvParser.parse(SKU_AND_NAME, csv);

        assertThat(parsed.isHeaderValid()).isTrue();
        assertThat(parsed.rows()).hasSize(2);
        assertThat(parsed.rows().get(0).rowNumber()).isEqualTo(1);
        assertThat(parsed.rows().get(1).rowNumber()).isEqualTo(3);
        assertThat(parsed.rowErrors()).hasSize(1);
        assertThat(parsed.rowErrors().get(0).rowNumber()).isEqualTo(2);
    }

    @Test
    void quotedFieldsMayContainCommas() {
        String csv = "sku,name\nSKU-1,\"Widget, Deluxe\"\n";

        ParsedCatalogCsv parsed = CatalogCsvParser.parse(SKU_AND_NAME, csv);

        assertThat(parsed.isHeaderValid()).isTrue();
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().get(0).valuesByColumnCode()).containsEntry("name", "Widget, Deluxe");
    }
}
