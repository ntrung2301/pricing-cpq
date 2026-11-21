package com.cpq.app.security;

import com.cpq.app.AbstractIntegrationTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S1-T8: the browser-based React frontend (Vite dev server,
 * {@code http://localhost:5173}) calls {@code GET /api/entities} directly
 * with a {@code fetch(...)} carrying an {@code Authorization: Bearer}
 * header — a cross-origin request (backend on 8080, frontend on 5173),
 * which browsers preflight with an unauthenticated {@code OPTIONS}
 * request. Without CORS configured, Spring Security's
 * {@code anyRequest().authenticated()} rejects that preflight with 401,
 * and the browser never even attempts the real GET (surfaces to the user
 * as "Failed to fetch", discovered manually via the real Playwright E2E
 * run for this task). This test pins down the fix: the preflight must be
 * permitted and answered with the right CORS headers for the frontend's
 * origin.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigurationIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void preflightFromFrontendOriginIsAllowedWithoutAuthentication() throws Exception {
        mockMvc.perform(
                        options("/api/entities")
                                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                        "http://localhost:5173"));
    }
}
