package com.houseofaje.catalog;

import java.math.BigDecimal;
import java.util.UUID;

/** Fields an admin supplies to create a service. Slug defaults to a name-derived value. */
public record CreateServiceRequest(
        String name,
        String slug,
        String description,
        int durationMinutes,
        BigDecimal price,
        int bufferMinutes,
        UUID categoryId) {}
