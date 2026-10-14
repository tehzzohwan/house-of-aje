package com.houseofaje.catalog;

import java.util.UUID;

/** A grouping label for services (e.g. "Lashes"). Categories are not bookable themselves. */
public record ServiceCategory(
        UUID id,
        String name,
        String slug,
        boolean active) {}
