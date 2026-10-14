package com.houseofaje.catalog;

import java.math.BigDecimal;
import java.util.UUID;

/** An active service offering, as shown to the public. */
public record Service(
        UUID id,
        String name,
        String slug,
        String description,
        int durationMinutes,
        BigDecimal price,
        UUID categoryId,
        String categoryName) {}
