package com.cpq.app.security;

import com.cpq.core.security.KeycloakRealmRoleConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter())));
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
