package com.cpq.app.pricing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.pricing.PricingStage;
import com.cpq.core.pricing.RuleType;
import com.cpq.core.pricing.persistence.PriceListEntity;
import com.cpq.core.pricing.persistence.PriceListItemEntity;
import com.cpq.core.pricing.persistence.RuleEntity;
import com.cpq.core.pricing.persistence.RuleSetEntity;
import com.cpq.core.security.KeycloakRealmRoleConverter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * HTTP-facing half of S2-T10/E3-02's Definition of Done, same pattern as
 * {@code EntitiesEndpointIT} (S1-T8): {@code POST /api/pricing/calculate}
 * round-tripped over real MockMvc + a Testcontainers Postgres, proving
 * the {@code @PreAuthorize} on {@code PricingCalculationService} is
 * actually enforced at the HTTP boundary (401 unauthenticated, 200 for
 * each of the 3 core roles) and that the JSON response shape matches
 * {@code PriceCalculationResponse}.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PricingEndpointIT {

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
    @Autowired private RuleSetRepository ruleSetRepository;
    @Autowired private PriceListRepository priceListRepository;
    @Autowired private PriceListItemRepository priceListItemRepository;

    private record Seeded(String ruleSetCode, String priceListCode, Long entityDefId) {}

    private Seeded seedMinimalPricingData() {
        EntityDefinition entityDefinition =
                entityDefinitionRepository.save(
                        new EntityDefinition("PE_WIDGET_" + UUID.randomUUID(), "PE Widget", 1));

        RuleSetEntity ruleSet = new RuleSetEntity("PE_RULESET_" + UUID.randomUUID(), 1);
        ruleSet.addRule(
                new RuleEntity(
                        PricingStage.DISCOUNT,
                        1,
                        RuleType.DSL,
                        "{\"action\": {\"type\": \"fixed-amount\", \"value\": -5, \"label\": \"flat-discount\"}}"));
        ruleSetRepository.save(ruleSet);

        PriceListEntity priceList =
                priceListRepository.save(
                        new PriceListEntity("PE_PRICELIST_" + UUID.randomUUID(), "PE Price List", "USD"));
        priceListItemRepository.save(
                new PriceListItemEntity(
                        priceList, entityDefinition.getId(), null, new BigDecimal("50.00"), null, null));

        return new Seeded(ruleSet.getCode(), priceList.getCode(), entityDefinition.getId());
    }

    private String requestBody(Seeded seeded) {
        return """
                {"ruleSetCode": "%s", "priceListCode": "%s", "entityDefId": %d, "entityInstanceId": null, "quantity": 1, "attributes": {}}
                """.formatted(seeded.ruleSetCode(), seeded.priceListCode(), seeded.entityDefId());
    }

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        Seeded seeded = seedMinimalPricingData();

        mockMvc.perform(
                        post("/api/pricing/calculate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(seeded)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCallerGetsFullWaterfallAndFinalAmount() throws Exception {
        Seeded seeded = seedMinimalPricingData();

        mockMvc.perform(
                        post("/api/pricing/calculate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(seeded))
                                .with(
                                        jwt().jwt(builder ->
                                                        builder.claim(
                                                                "realm_access",
                                                                Map.of("roles", List.of("sales"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalAmount").value(45.00))
                .andExpect(jsonPath("$.steps[0].label").value("List price"))
                .andExpect(jsonPath("$.steps[1].label").value("flat-discount"))
                .andExpect(jsonPath("$.steps[2].label").value("Final price"))
                .andExpect(jsonPath("$.steps[2].rounded").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sales", "sales_manager", "pricing_admin"})
    void anyOfTheThreeCoreRolesCanCallTheEndpoint(String keycloakRole) throws Exception {
        Seeded seeded = seedMinimalPricingData();

        mockMvc.perform(
                        post("/api/pricing/calculate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(seeded))
                                .with(
                                        jwt().jwt(builder ->
                                                        builder.claim(
                                                                "realm_access",
                                                                Map.of("roles", List.of(keycloakRole))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isOk());
    }
}
