package com.cpq.app.pricing.pricelist;

import com.cpq.app.AbstractIntegrationTest;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.cpq.app.pricing.PriceListItemV2Repository;
import com.cpq.app.pricing.PriceListV2Repository;
import com.cpq.core.pricing.dag.DagNode;
import com.cpq.core.pricing.persistence.PriceListItemV2Entity;
import com.cpq.core.pricing.persistence.PriceListV2Entity;
import com.cpq.core.security.KeycloakRealmRoleConverter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S3-T6 RBAC Definition of Done (this task's instructions: "RBAC: theo
 * đúng pattern đã có — PRICING_ADMIN cho hành động tạo/submit/approve/
 * override"): all 5 {@code /api/price-lists-v2/...} endpoints must
 * return 401 unauthenticated, 403 for a non-{@code PRICING_ADMIN} role,
 * 200 for {@code PRICING_ADMIN} — same MockMvc + real JWT-authorities
 * pattern as {@code ImportEndpointSecurityIT} (S2-T5).
 *
 * <p>Fixture rows (price list / item) are seeded directly via {@link
 * PriceListV2Repository}/{@link PriceListItemV2Repository}, bypassing
 * {@code PriceListService} itself — same convention as {@code
 * ImportEndpointSecurityIT} seeding its {@code entity_def} directly via
 * repository, since the point of this test is the HTTP/RBAC layer, not
 * re-exercising the DAG execution path (covered by {@code
 * PriceListServiceImplIT}).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PriceListEndpointSecurityIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PriceListV2Repository priceListV2Repository;
    @Autowired private PriceListItemV2Repository priceListItemV2Repository;

    private static final String MINIMAL_GRAPH_JSON =
            "\"calculationGraph\": [{\"id\":\"n1\",\"type\":\"output\",\"config\":{},\"inputs\":{}}],"
                    + "\"resultNodeId\": \"n1\"";

    private PriceListV2Entity seedDraftPriceList(String code) {
        List<DagNode> graph = List.of(new DagNode("n1", "output", Map.of(), Map.of()));
        PriceListV2Entity entity =
                new PriceListV2Entity(code, "Security Test Price List", "USD", null, null, graph, "n1", "seed");
        return priceListV2Repository.save(entity);
    }

    private PriceListItemV2Entity seedItem(PriceListV2Entity priceList) {
        PriceListItemV2Entity item = new PriceListItemV2Entity(priceList, 1L, null, null);
        return priceListItemV2Repository.save(item);
    }

    @Test
    void createPriceListUnauthenticatedIs401() throws Exception {
        mockMvc.perform(
                        post("/api/price-lists-v2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"code\":\"SEC_"
                                                + UUID.randomUUID()
                                                + "\",\"name\":\"n\",\"currency\":\"USD\","
                                                + MINIMAL_GRAPH_JSON
                                                + "}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPriceListWrongRoleIs403() throws Exception {
        mockMvc.perform(
                        post("/api/price-lists-v2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"code\":\"SEC_"
                                                + UUID.randomUUID()
                                                + "\",\"name\":\"n\",\"currency\":\"USD\","
                                                + MINIMAL_GRAPH_JSON
                                                + "}")
                                .with(
                                        jwt().jwt(
                                                        builder ->
                                                                builder.claim(
                                                                        "realm_access", Map.of("roles", List.of("sales"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPriceListPricingAdminIs200() throws Exception {
        mockMvc.perform(
                        post("/api/price-lists-v2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"code\":\"SEC_"
                                                + UUID.randomUUID()
                                                + "\",\"name\":\"n\",\"currency\":\"USD\","
                                                + MINIMAL_GRAPH_JSON
                                                + "}")
                                .with(
                                        jwt().jwt(
                                                        builder ->
                                                                builder.claim(
                                                                        "realm_access",
                                                                        Map.of("roles", List.of("pricing_admin"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isOk());
    }

    @Test
    void addItemUnauthenticatedIs401() throws Exception {
        PriceListV2Entity priceList = seedDraftPriceList("SEC_" + UUID.randomUUID());
        mockMvc.perform(
                        post("/api/price-lists-v2/{id}/items", priceList.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entityInstanceId\": 1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addItemWrongRoleIs403() throws Exception {
        PriceListV2Entity priceList = seedDraftPriceList("SEC_" + UUID.randomUUID());
        mockMvc.perform(
                        post("/api/price-lists-v2/{id}/items", priceList.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entityInstanceId\": 1}")
                                .with(
                                        jwt().jwt(
                                                        builder ->
                                                                builder.claim(
                                                                        "realm_access", Map.of("roles", List.of("sales_manager"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitUnauthenticatedIs401() throws Exception {
        PriceListV2Entity priceList = seedDraftPriceList("SEC_" + UUID.randomUUID());
        mockMvc.perform(post("/api/price-lists-v2/{id}/submit", priceList.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitWrongRoleIs403() throws Exception {
        PriceListV2Entity priceList = seedDraftPriceList("SEC_" + UUID.randomUUID());
        mockMvc.perform(
                        post("/api/price-lists-v2/{id}/submit", priceList.getId())
                                .with(
                                        jwt().jwt(
                                                        builder ->
                                                                builder.claim(
                                                                        "realm_access", Map.of("roles", List.of("sales"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitPricingAdminIs200() throws Exception {
        PriceListV2Entity priceList = seedDraftPriceList("SEC_" + UUID.randomUUID());
        mockMvc.perform(
                        post("/api/price-lists-v2/{id}/submit", priceList.getId())
                                .with(
                                        jwt().jwt(
                                                        builder ->
                                                                builder.claim(
                                                                        "realm_access",
                                                                        Map.of("roles", List.of("pricing_admin"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isOk());
    }

    @Test
    void approveUnauthenticatedIs401() throws Exception {
        PriceListV2Entity priceList = seedDraftPriceList("SEC_" + UUID.randomUUID());
        mockMvc.perform(post("/api/price-lists-v2/{id}/approve", priceList.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approveWrongRoleIs403() throws Exception {
        PriceListV2Entity priceList = seedDraftPriceList("SEC_" + UUID.randomUUID());
        mockMvc.perform(
                        post("/api/price-lists-v2/{id}/approve", priceList.getId())
                                .with(
                                        jwt().jwt(
                                                        builder ->
                                                                builder.claim(
                                                                        "realm_access", Map.of("roles", List.of("sales"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isForbidden());
    }

    @Test
    void overrideUnauthenticatedIs401() throws Exception {
        PriceListV2Entity priceList = seedDraftPriceList("SEC_" + UUID.randomUUID());
        PriceListItemV2Entity item = seedItem(priceList);
        mockMvc.perform(
                        patch("/api/price-lists-v2/items/{itemId}/override", item.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"manualOverridePrice\": 1.00}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void overrideWrongRoleIs403() throws Exception {
        PriceListV2Entity priceList = seedDraftPriceList("SEC_" + UUID.randomUUID());
        PriceListItemV2Entity item = seedItem(priceList);
        mockMvc.perform(
                        patch("/api/price-lists-v2/items/{itemId}/override", item.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"manualOverridePrice\": 1.00}")
                                .with(
                                        jwt().jwt(
                                                        builder ->
                                                                builder.claim(
                                                                        "realm_access", Map.of("roles", List.of("sales"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isForbidden());
    }

    @Test
    void overridePricingAdminIs200() throws Exception {
        PriceListV2Entity priceList = seedDraftPriceList("SEC_" + UUID.randomUUID());
        PriceListItemV2Entity item = seedItem(priceList);
        mockMvc.perform(
                        patch("/api/price-lists-v2/items/{itemId}/override", item.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"manualOverridePrice\": 1.00}")
                                .with(
                                        jwt().jwt(
                                                        builder ->
                                                                builder.claim(
                                                                        "realm_access",
                                                                        Map.of("roles", List.of("pricing_admin"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isOk());
    }

    // --- S3-T8: GET /api/price-lists-v2/{id} (docs/decisions.md D-049) ---
    // Read-only, so same RBAC level as GET /api/catalog/{code}/instances
    // (D-030): any of the 3 core roles may call it, not PRICING_ADMIN-only.

    @Test
    void getPriceListUnauthenticatedIs401() throws Exception {
        PriceListV2Entity priceList = seedDraftPriceList("SEC_" + UUID.randomUUID());
        mockMvc.perform(get("/api/price-lists-v2/{id}", priceList.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPriceListWrongRoleIs403() throws Exception {
        PriceListV2Entity priceList = seedDraftPriceList("SEC_" + UUID.randomUUID());
        mockMvc.perform(
                        get("/api/price-lists-v2/{id}", priceList.getId())
                                .with(
                                        jwt().jwt(
                                                        builder ->
                                                                builder.claim(
                                                                        "realm_access", Map.of("roles", List.of("nobody"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPriceListSalesIs200() throws Exception {
        PriceListV2Entity priceList = seedDraftPriceList("SEC_" + UUID.randomUUID());
        mockMvc.perform(
                        get("/api/price-lists-v2/{id}", priceList.getId())
                                .with(
                                        jwt().jwt(
                                                        builder ->
                                                                builder.claim(
                                                                        "realm_access", Map.of("roles", List.of("sales"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isOk());
    }

    @Test
    void getPriceListPricingAdminIs200() throws Exception {
        PriceListV2Entity priceList = seedDraftPriceList("SEC_" + UUID.randomUUID());
        mockMvc.perform(
                        get("/api/price-lists-v2/{id}", priceList.getId())
                                .with(
                                        jwt().jwt(
                                                        builder ->
                                                                builder.claim(
                                                                        "realm_access",
                                                                        Map.of("roles", List.of("pricing_admin"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isOk());
    }

    @Test
    void getPriceListOnUnknownIdIs404() throws Exception {
        mockMvc.perform(
                        get("/api/price-lists-v2/{id}", -1L)
                                .with(
                                        jwt().jwt(
                                                        builder ->
                                                                builder.claim(
                                                                        "realm_access",
                                                                        Map.of("roles", List.of("pricing_admin"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/price-lists-v2 (list, docs/decisions.md D-054) ---
    // Same read-only RBAC as GET /api/price-lists-v2/{id} above.

    @Test
    void listPriceListsUnauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/price-lists-v2")).andExpect(status().isUnauthorized());
    }

    @Test
    void listPriceListsWrongRoleIs403() throws Exception {
        mockMvc.perform(
                        get("/api/price-lists-v2")
                                .with(
                                        jwt().jwt(
                                                        builder ->
                                                                builder.claim(
                                                                        "realm_access", Map.of("roles", List.of("nobody"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isForbidden());
    }

    @Test
    void listPriceListsSalesManagerIs200() throws Exception {
        mockMvc.perform(
                        get("/api/price-lists-v2")
                                .with(
                                        jwt().jwt(
                                                        builder ->
                                                                builder.claim(
                                                                        "realm_access",
                                                                        Map.of("roles", List.of("sales_manager"))))
                                                .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isOk());
    }
}
