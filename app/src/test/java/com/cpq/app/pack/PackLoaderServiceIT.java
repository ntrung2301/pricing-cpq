package com.cpq.app.pack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.EntityDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

    private static AttributeDefinition findByCode(EntityDefinition entityDefinition, String code) {
        return entityDefinition.getAttributes().stream()
                .filter(attribute -> attribute.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }
}
