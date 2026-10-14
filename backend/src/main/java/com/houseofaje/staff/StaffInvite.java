package com.houseofaje.staff;

import java.time.Instant;
import java.util.UUID;

public record StaffInvite(
        UUID id,
        UUID staffId,
        String token,
        Instant expiresAt,
        Instant usedAt,
        Instant createdAt) {}
