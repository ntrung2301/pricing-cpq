package com.cpq.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.cpq.core.api.catalog.CatalogImportService;
import com.cpq.core.api.catalog.ImportResultDto;
import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityInstance;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S2-T5 (E1-04) Definition of Done (sprint-2.md): "Import CSV đúng cột →
 * tạo đúng entity_instance kèm business_key; CSV thiếu cột bắt buộc hoặc
 * sai tên cột → báo lỗi rõ theo dòng, job vẫn hoàn tất với danh sách lỗi,
 * không crash". Uses its own ad-hoc "TestWidget"-style entity_def (created
 * directly via {@code EntityDefinitionRepository}, not the automotive
 * pack's Product/Customer, which S2-T3/S2-T4 define independently) so this
 * test runs standalone regardless of those tasks' progress.
 *
 * <p>Same Testcontainers Postgres pattern as {@code EntityInstanceServiceIT}
 * (S2-T1): one schema per test class, so {@code entity_def.code} must be
 * unique per test method. {@code @WithMockUser} supplies the
 * {@code PRICING_ADMIN} authority {@link com.cpq.core.api.catalog.CatalogImportService#importCsv}
 * requires (ADR-006) — RBAC's 401/403/200 HTTP behavior itself is
 * exercised separately in {@code ImportEndpointSecurityIT} (MockMvc, no
 * real role here needed to drive those HTTP-layer assertions).
 */
@Testcontainers
@SpringBootTest
@WithMockUser(roles = "PRICING_ADMIN")
class CatalogImportServiceImplIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private com.cpq.app.metamodel.EntityDefinitionRepository entityDefinitionRepository;
    @Autowired private CatalogImportService catalogImportService;
    @Autowired private ImportJobRepository importJobRepository;
    @Autowired private ImportJobRowErrorRepository importJobRowErrorRepository;
    @Autowired private com.cpq.app.metamodel.EntityInstanceRepository entityInstanceRepository;

    private EntityDefinition defineWidget(String uniqueCode) {
        AttributeDefinition sku =
                new AttributeDefinition("sku", "SKU", AttributeDataType.STRING, true, null);
        AttributeDefinition name =
                new AttributeDefinition("name", "Name", AttributeDataType.STRING, true, null);
        AttributeDefinition qty =
                new AttributeDefinition("qty", "Quantity", AttributeDataType.NUMBER, false, null);
        EntityDefinition entityDefinition = new EntityDefinition(uniqueCode, "Test Widget", 1);
        entityDefinition.addAttribute(sku);
        entityDefinition.addAttribute(name);
        entityDefinition.addAttribute(qty);
        EntityDefinition saved = entityDefinitionRepository.save(entityDefinition);
        AttributeDefinition savedSku = findByCode(saved, "sku");
        AttributeDefinition savedName = findByCode(saved, "name");
        saved.designateKeyAttribute(savedSku);
        saved.designateLabelAttribute(savedName);
        return entityDefinitionRepository.save(saved);
    }

    private static AttributeDefinition findByCode(EntityDefinition entityDefinition, String code) {
        return entityDefinition.getAttributes().stream()
                .filter(a -> a.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private static String uniqueCode(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    void validCsvImportsEveryRowAndMarksJobCompleted() {
        String code = uniqueCode("CIS_WIDGET");
        defineWidget(code);
        String csv = "sku,name,qty\nSKU-1,Widget One,10\nSKU-2,Widget Two,20\n";

        ImportResultDto result = catalogImportService.importCsv(code, "widgets.csv", csv);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.createdCount()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();

        List<EntityInstance> instances = entityInstanceRepository.findByEntityDefinition_Code(code);
        assertThat(instances)
                .extracting(EntityInstance::getBusinessKey)
                .containsExactlyInAnyOrder("SKU-1", "SKU-2");
    }

    @Test
    void unknownHeaderColumnFailsTheWholeJobWithNoRowsImported() {
        String code = uniqueCode("CIS_WIDGET");
        defineWidget(code);
        String csv = "sku,nmae,qty\nSKU-1,Widget One,10\n";

        ImportResultDto result = catalogImportService.importCsv(code, "widgets.csv", csv);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.createdCount()).isZero();
        assertThat(result.errors())
                .anySatisfy(
                        error -> {
                            assertThat(error.rowNumber()).isEqualTo(0);
                            assertThat(error.columnName()).isEqualTo("nmae");
                        });
    }

    @Test
    void missingRequiredColumnFailsTheWholeJobWithNoRowsImported() {
        String code = uniqueCode("CIS_WIDGET");
        defineWidget(code);
        String csv = "sku,qty\nSKU-1,10\n"; // "name" (required) entirely absent

        ImportResultDto result = catalogImportService.importCsv(code, "widgets.csv", csv);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.createdCount()).isZero();
        assertThat(result.errors())
                .anySatisfy(
                        error -> {
                            assertThat(error.rowNumber()).isEqualTo(0);
                            assertThat(error.columnName()).isEqualTo("name");
                        });
    }

    @Test
    void oneInvalidRowIsReportedButJobStillCompletesAndOtherRowsImport() {
        String code = uniqueCode("CIS_WIDGET");
        defineWidget(code);
        String csv = "sku,name,qty\nSKU-1,Widget One,10\nSKU-2,Widget Two,not-a-number\nSKU-3,Widget Three,30\n";

        ImportResultDto result = catalogImportService.importCsv(code, "widgets.csv", csv);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.createdCount()).isEqualTo(2);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).rowNumber()).isEqualTo(2);
        assertThat(result.errors().get(0).columnName()).isEqualTo("qty");

        List<EntityInstance> instances = entityInstanceRepository.findByEntityDefinition_Code(code);
        assertThat(instances)
                .extracting(EntityInstance::getBusinessKey)
                .containsExactlyInAnyOrder("SKU-1", "SKU-3");
    }

    @Test
    void duplicateBusinessKeyWithinTheSameFileIsReportedAsARowErrorNotACrash() {
        String code = uniqueCode("CIS_WIDGET");
        defineWidget(code);
        String csv = "sku,name,qty\nSKU-1,Widget One,10\nSKU-1,Widget Duplicate,20\n";

        ImportResultDto result = catalogImportService.importCsv(code, "widgets.csv", csv);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).rowNumber()).isEqualTo(2);
    }

    @Test
    void jobAndRowErrorsArePersisted() {
        String code = uniqueCode("CIS_WIDGET");
        defineWidget(code);
        String csv = "sku,name,qty\nSKU-1,Widget One,not-a-number\n";

        ImportResultDto result = catalogImportService.importCsv(code, "widgets.csv", csv);

        var job = importJobRepository.findById(result.jobId()).orElseThrow();
        assertThat(job.getFileName()).isEqualTo("widgets.csv");
        var rowErrors = importJobRowErrorRepository.findAll();
        assertThat(rowErrors).anySatisfy(e -> assertThat(e.getImportJob().getId()).isEqualTo(job.getId()));
    }
}
