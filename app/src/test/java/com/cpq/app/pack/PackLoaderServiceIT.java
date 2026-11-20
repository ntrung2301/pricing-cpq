package com.cpq.app.pack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cpq.app.metamodel.EntityDefinitionDimensionAttributeRepository;
import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityDefinitionDimensionAttribute;
import com.cpq.core.metamodel.EntityRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S2-T1 Definition of Done (sprint-2.md): "entity_def khai báo attribute
 * thứ 21 → PackLoaderService từ chối nạp pack, thông báo lỗi rõ" + ADR-001
 * "PackLoaderService nạp lúc khởi động (classpath scan
 * config/packs/*&#47;pack.yaml) ... upsert vào bảng metamodel, mỗi dòng
 * gắn pack_id + pack_version". Follows the same Testcontainers pattern as
 * {@code MetamodelDefinitionServiceIT} (S1-T7).
 */
@Testcontainers
@SpringBootTest
class PackLoaderServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private PackLoaderService packLoaderService;
    @Autowired private EntityDefinitionRepository entityDefinitionRepository;
    @Autowired private EntityDefinitionDimensionAttributeRepository dimensionAttributeRepository;

    @Test
    void loadPackUpsertsEntityWithAttributesAndKeyLabelPointersTaggedWithPackIdAndVersion() {
        String yaml =
                """
                pack_id: direct-load-test-pack
                version: 2
                entities:
                  - code: PlDirectWidget
                    label: PL Direct Widget
                    key_attribute: sku
                    label_attribute: name
                    attributes:
                      - code: sku
                        label: SKU
                        data_type: STRING
                        required: true
                      - code: name
                        label: Name
                        data_type: STRING
                        required: true
                """;

        List<EntityDefinition> loaded = packLoaderService.loadPack(yaml);

        assertThat(loaded).hasSize(1);
        EntityDefinition saved =
                entityDefinitionRepository.findByCodeWithAttributes("PlDirectWidget").orElseThrow();
        assertThat(saved.getPackId()).isEqualTo("direct-load-test-pack");
        assertThat(saved.getPackVersion()).isEqualTo(2);
        assertThat(saved.getAttributes()).hasSize(2);

        AttributeDefinition sku = findByCode(saved, "sku");
        AttributeDefinition name = findByCode(saved, "name");
        assertThat(sku.getPackId()).isEqualTo("direct-load-test-pack");
        assertThat(saved.getKeyAttributeDefinition().getId()).isEqualTo(sku.getId());
        assertThat(saved.getLabelAttributeDefinition().getId()).isEqualTo(name.getId());
    }

    @Test
    void loadPackRejectsAnEntityDeclaringMoreThan20Attributes() {
        StringBuilder yaml =
                new StringBuilder(
                        """
                        pack_id: cap-rejection-test-pack
                        version: 1
                        entities:
                          - code: PlTooManyAttributesWidget
                            label: PL Too Many Attributes Widget
                            attributes:
                        """);
        for (int i = 1; i <= 21; i++) {
            yaml.append("      - code: attr_")
                    .append(i)
                    .append("\n        label: Attr ")
                    .append(i)
                    .append("\n        data_type: STRING\n        required: false\n");
        }

        assertThatThrownBy(() -> packLoaderService.loadPack(yaml.toString()))
                .isInstanceOf(PackLoadException.class)
                .hasMessageContaining("21")
                .hasMessageContaining("PlTooManyAttributesWidget");

        assertThat(entityDefinitionRepository.findByCodeWithAttributes("PlTooManyAttributesWidget"))
                .isEmpty();
    }

    @Test
    void loadAllPacksFromClasspathPicksUpTheSyntheticTestFixturePack() throws Exception {
        packLoaderService.loadAllPacksFromClasspath();

        EntityDefinition saved =
                entityDefinitionRepository.findByCodeWithAttributes("PackLoaderSyntheticWidget").orElseThrow();
        assertThat(saved.getPackId()).isEqualTo("synthetic-test-pack");
        assertThat(saved.getPackVersion()).isEqualTo(1);
        assertThat(saved.getKeyAttributeDefinition().getCode()).isEqualTo("widget_key");
        assertThat(saved.getLabelAttributeDefinition().getCode()).isEqualTo("widget_name");
    }

    @Test
    @Transactional // avoid LazyInitializationException reading lazy AttributeDefinition/EntityDefinition
    // associations below outside the (closed-by-then) transaction PackLoaderService#loadPack ran in —
    // same reasoning/pattern as AutomotivePackIT's test method.
    void loadPackAssignsRoleAndExtendsPointerAndOrderedDimensionAttributes() {
        String yaml =
                """
                pack_id: taxonomy-extension-test-pack
                version: 1
                entities:
                  - code: PlTaxonomyAnchor
                    label: PL Taxonomy Anchor
                    key_attribute: anchor_code
                    attributes:
                      - code: anchor_code
                        label: Anchor Code
                        data_type: STRING
                        required: true
                  - code: PlTaxonomyExtension
                    label: PL Taxonomy Extension
                    role: EXTENSION
                    extends: PlTaxonomyAnchor
                    dimension_attributes: [period, city]
                    attributes:
                      - code: period
                        label: Period
                        data_type: STRING
                        required: true
                      - code: city
                        label: City
                        data_type: STRING
                        required: true
                """;

        packLoaderService.loadPack(yaml);

        EntityDefinition anchor =
                entityDefinitionRepository.findByCodeWithAttributes("PlTaxonomyAnchor").orElseThrow();
        EntityDefinition extension =
                entityDefinitionRepository.findByCodeWithAttributes("PlTaxonomyExtension").orElseThrow();

        assertThat(extension.getRole()).isEqualTo(EntityRole.EXTENSION);
        assertThat(extension.getExtendsEntityDefinition().getId()).isEqualTo(anchor.getId());

        List<EntityDefinitionDimensionAttribute> dimensions =
                dimensionAttributeRepository.findByEntityDefinitionOrderByPositionAsc(extension);
        assertThat(dimensions).hasSize(2);
        assertThat(dimensions.get(0).getAttributeDefinition().getCode()).isEqualTo("period");
        assertThat(dimensions.get(0).getPosition()).isZero();
        assertThat(dimensions.get(1).getAttributeDefinition().getCode()).isEqualTo("city");
        assertThat(dimensions.get(1).getPosition()).isEqualTo(1);
    }

    @Test
    void loadPackRejectsExtensionEntityWhoseExtendsPointsToAnUnknownEntity() {
        String yaml =
                """
                pack_id: taxonomy-unknown-extends-test-pack
                version: 1
                entities:
                  - code: PlOrphanExtension
                    label: PL Orphan Extension
                    role: EXTENSION
                    extends: PlDoesNotExist
                    dimension_attributes: [period]
                    attributes:
                      - code: period
                        label: Period
                        data_type: STRING
                        required: true
                """;

        assertThatThrownBy(() -> packLoaderService.loadPack(yaml))
                .isInstanceOf(PackLoadException.class)
                .hasMessageContaining("PlOrphanExtension")
                .hasMessageContaining("PlDoesNotExist");
    }

    @Test
    void loadPackRejectsExtensionEntityWhoseDimensionAttributeIsNotOneOfItsOwnAttributes() {
        String yaml =
                """
                pack_id: taxonomy-bad-dimension-test-pack
                version: 1
                entities:
                  - code: PlDimAnchor
                    label: PL Dim Anchor
                    key_attribute: anchor_code
                    attributes:
                      - code: anchor_code
                        label: Anchor Code
                        data_type: STRING
                        required: true
                  - code: PlBadDimensionExtension
                    label: PL Bad Dimension Extension
                    role: EXTENSION
                    extends: PlDimAnchor
                    dimension_attributes: [not_declared]
                    attributes:
                      - code: period
                        label: Period
                        data_type: STRING
                        required: true
                """;

        assertThatThrownBy(() -> packLoaderService.loadPack(yaml))
                .isInstanceOf(PackLoadException.class)
                .hasMessageContaining("PlBadDimensionExtension")
                .hasMessageContaining("not_declared");
    }

    @Test
    void loadPackIgnoresRoleDeclaredForProductOrCustomerEntityCodes() {
        String yaml =
                """
                pack_id: taxonomy-anchor-role-override-test-pack
                version: 1
                entities:
                  - code: Product
                    label: Product Role Override Attempt
                    role: PARAMETER
                    attributes:
                      - code: pl_override_sku
                        label: SKU
                        data_type: STRING
                        required: true
                """;

        // Every ApplicationRunner bean in `app` (CoreAnchorBootstrapService,
        // PackLoaderService itself loading the real automotive pack.yaml via
        // its own @Order(1)/@Order(2) run()) already executed automatically
        // at this @SpringBootTest's context startup (D-040, correcting
        // D-020 point 6) — so `Product` is NOT in a pristine "role never
        // set" state by the time this test body runs; it is realistically
        // already `role=ANCHOR`. What this test actually needs to prove is
        // narrower and ambient-state-independent: THIS pack's `role:
        // PARAMETER` declaration has zero effect — whatever role Product
        // had immediately before this specific loadPack call is exactly
        // what it has immediately after.
        EntityRole roleBeforeThisLoad =
                entityDefinitionRepository.findByCodeWithAttributes("Product").map(EntityDefinition::getRole).orElse(null);

        packLoaderService.loadPack(yaml);

        EntityDefinition product = entityDefinitionRepository.findByCodeWithAttributes("Product").orElseThrow();
        // Never overwritten to PARAMETER as the pack tried to (D-039) —
        // role for Product/Customer is exclusively CoreAnchorBootstrapService's
        // job.
        assertThat(product.getRole()).isNotEqualTo(EntityRole.PARAMETER);
        assertThat(product.getRole()).isEqualTo(roleBeforeThisLoad);
        // The attribute itself still gets added — only `role` is ignored.
        assertThat(product.getAttributes()).extracting(AttributeDefinition::getCode).contains("pl_override_sku");
    }

    private static AttributeDefinition findByCode(EntityDefinition entityDefinition, String code) {
        return entityDefinition.getAttributes().stream()
                .filter(attribute -> attribute.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }
}
