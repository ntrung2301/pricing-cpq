package com.cpq.app.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.security.KeycloakRealmRoleConverter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ADR-006 / S2-T5 Definition of Done: {@code POST
 * /api/catalog/{entityDefCode}/import} must return 401/403/200 per role,
 * exactly like {@code PingEndpointSecurityIT} (S1-T6) and consistent with
 * this task's RBAC scope decision — only {@code PRICING_ADMIN} may import
 * (docs/decisions.md).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ImportEndpointSecurityIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityDefinitionRepository entityDefinitionRepository;

    private String defineWidget() {
        String code = "IES_WIDGET_" + UUID.randomUUID().toString().replace("-", "");
        AttributeDefinition sku =
                new AttributeDefinition("sku", "SKU", AttributeDataType.STRING, true, null);
        EntityDefinition entityDefinition = new EntityDefinition(code, "IES Widget", 1);
        entityDefinition.addAttribute(sku);
        entityDefinitionRepository.save(entityDefinition);
        return code;
    }

    private static MockMultipartFile csvFile() {
        return new MockMultipartFile(
                "file", "widgets.csv", "text/csv", "sku\nSKU-1\n".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        String code = defineWidget();
        mockMvc.perform(multipart("/api/catalog/{code}/import", code).file(csvFile()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongRoleIsRejectedWith403() throws Exception {
        String code = defineWidget();
        mockMvc.perform(
                        multipart("/api/catalog/{code}/import", code)
                                .file(csvFile())
                                .with(
                                        jwt().jwt(builder ->
                                                        builder.claim(
                                                                "realm_access",
                                                                Map.of("roles", List.of("sales"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isForbidden());
    }

    @Test
    void pricingAdminRoleIsAcceptedWith200() throws Exception {
        String code = defineWidget();
        mockMvc.perform(
                        multipart("/api/catalog/{code}/import", code)
                                .file(csvFile())
                                .with(
                                        jwt().jwt(builder ->
                                                        builder.claim(
                                                                "realm_access",
                                                                Map.of(
                                                                        "roles",
                                                                        List.of("pricing_admin"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isOk());
    }
}
