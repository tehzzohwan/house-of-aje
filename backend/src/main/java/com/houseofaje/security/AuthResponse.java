package com.houseofaje.security;

/**
 * Public auth response. The JWT itself is delivered as an HttpOnly cookie; the
 * JSON body carries only the non-sensitive role (or {@code null} when
 * unauthenticated, as returned by {@code GET /api/auth/me}).
 */
public record AuthResponse(String role) {}
