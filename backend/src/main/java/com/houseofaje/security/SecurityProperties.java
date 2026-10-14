package com.houseofaje.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT and admin credential configuration. The admin credential is a single
 * configured account (env-driven); replace with a real admin store before
 * production. The JWT secret must be at least 32 bytes for HS256.
 */
@ConfigurationProperties(prefix = "hoa.security")
public record SecurityProperties(
        String jwtSecret,
        Duration jwtTtl,
        String adminUsername,
        String adminPassword) {}
