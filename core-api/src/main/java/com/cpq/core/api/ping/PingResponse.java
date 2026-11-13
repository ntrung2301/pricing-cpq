package com.cpq.core.api.ping;

/**
 * Response DTO for the S1-T6 skeleton verification endpoint
 * {@code GET /api/ping}. {@code caller} is whatever
 * {@link PingService#ping()} returns (the JWT {@code sub}/principal
 * name) — just enough to prove, from the HTTP response body, that
 * authentication/authorization actually ran.
 */
public record PingResponse(String status, String caller) {
}
