package com.houseofaje.catalog;

import java.math.BigDecimal;
import java.util.UUID;

/** A service as seen in the admin console, including inactive ones and metadata. */
public record AdminService(
        UUID id,
        String name,
        String slug,
        String description,
        int durationMinutes,
        BigDecimal price,
        int bufferMinutes,
        boolean active,
        UUID categoryId,
        String categoryName) {}
