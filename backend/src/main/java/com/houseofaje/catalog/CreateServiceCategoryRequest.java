package com.houseofaje.catalog;

/** Fields an admin supplies to create a service category. Slug defaults to a name-derived value. */
public record CreateServiceCategoryRequest(
        String name,
        String slug) {}
