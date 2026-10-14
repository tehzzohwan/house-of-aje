package com.houseofaje.catalog;

import java.util.UUID;

public class ServiceCategoryNotFoundException extends RuntimeException {

    public ServiceCategoryNotFoundException(UUID id) {
        super("Category not found: " + id);
    }
}
