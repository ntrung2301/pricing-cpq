package com.cpq.app.security;

import com.cpq.core.security.KeycloakRealmRoleConverter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security wiring for ADR-006: a stateless OAuth2 resource server
 * that validates JWTs issued by Keycloak, with
 * {@link KeycloakRealmRoleConverter} bridging Keycloak's realm roles to
 * {@code ROLE_*} Spring authorities.
 *
 * <p>Kept in {@code app} (the composition root) rather than
 * {@code core-security}, consistent with this project's existing
 * precedent of keeping concrete Spring Boot wiring/bootstrapping in
 * {@code app} (e.g. S1-T7's {@code EntityDefinitionRepository}/JPA/Flyway
 * wiring) while {@code core-security} holds the reusable, non-bootstrapping
 * pieces ({@link com.cpq.core.security.CpqRole},
 * {@link KeycloakRealmRoleConverter}).
 *
 * <p>{@code @EnableMethodSecurity} turns on {@code @PreAuthorize} on
 * {@code @Service} methods (ADR-006: "enforce ở method layer (service,
 * không chỉ controller) để chặn cả khi có đường gọi nội bộ khác HTTP") —
 * see {@code com.cpq.core.api.ping.PingService#ping()} for the S1-T6
 * skeleton-verification example.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * S1-T8: the React frontend (Vite dev server) calls {@code GET
     * /api/entities} directly from the browser via {@code fetch(...)} with
     * an {@code Authorization: Bearer} header — a cross-origin request
     * (frontend on port 5173, backend on port 8080), which browsers
     * preflight with an unauthenticated {@code OPTIONS} request. Without
     * this, {@code anyRequest().authenticated()} below rejects the
     * preflight itself with 401 and the browser never attempts the real
     * request (discovered via this task's Playwright E2E run, which failed
     * with "Failed to fetch" before this was added). Overridable via
     * {@code CPQ_WEB_ORIGIN} (same overridable-default pattern as
     * {@code KEYCLOAK_ISSUER_URI}, D-017) so this isn't hardcoded to one
     * developer's machine.
     */
    @Value("${CPQ_WEB_ORIGIN:http://localhost:5173}")
    private String webOrigin;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter())));
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(webOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
