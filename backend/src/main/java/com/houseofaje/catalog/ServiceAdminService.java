package com.houseofaje.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin catalogue management: list, create and toggle service availability. */
@Service
public class ServiceAdminService {

    private final ServiceRepository services;
    private final ServiceCategoryRepository categories;

    public ServiceAdminService(ServiceRepository services, ServiceCategoryRepository categories) {
        this.services = services;
        this.categories = categories;
    }

    public List<AdminService> list() {
        return services.findAll();
    }

    @Transactional
    public AdminService create(CreateServiceRequest request) {
        String name = request.name() == null ? null : request.name().trim();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (request.durationMinutes() <= 0) {
            throw new IllegalArgumentException("durationMinutes must be positive");
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("price must be zero or positive");
        }
        if (request.bufferMinutes() < 0) {
            throw new IllegalArgumentException("bufferMinutes must not be negative");
        }
        BigDecimal price = request.price().setScale(2, RoundingMode.HALF_UP);
        String slug = request.slug() == null || request.slug().isBlank()
                ? slugify(name)
                : request.slug().trim();
        if (services.slugExists(slug)) {
            throw new IllegalArgumentException("A service with this name already exists");
        }
        String description = request.description() == null ? null : request.description().trim();
        UUID categoryId = request.categoryId();
        if (categoryId != null && categories.findById(categoryId).isEmpty()) {
            throw new IllegalArgumentException("Unknown category id: " + categoryId);
        }
        UUID id = UUID.randomUUID();
        services.insert(id, name, slug, description, request.durationMinutes(), price, request.bufferMinutes(), true, categoryId);
        return services.findById(id).orElseThrow(() -> new ServiceNotFoundException(id));
    }

    @Transactional
    public AdminService setActive(UUID id, boolean active) {
        if (!services.setActive(id, active)) {
            throw new ServiceNotFoundException(id);
        }
        return services.findById(id).orElseThrow(() -> new ServiceNotFoundException(id));
    }

    @Transactional
    public AdminService update(UUID id, CreateServiceRequest request) {
        if (services.findById(id).isEmpty()) {
            throw new ServiceNotFoundException(id);
        }
        String name = request.name() == null ? null : request.name().trim();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (request.durationMinutes() <= 0) {
            throw new IllegalArgumentException("durationMinutes must be positive");
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("price must be zero or positive");
        }
        if (request.bufferMinutes() < 0) {
            throw new IllegalArgumentException("bufferMinutes must not be negative");
        }
        BigDecimal price = request.price().setScale(2, RoundingMode.HALF_UP);
        String slug = request.slug() == null || request.slug().isBlank()
                ? slugify(name)
                : request.slug().trim();
        if (services.slugExists(slug, id)) {
            throw new IllegalArgumentException("A service with this name already exists");
        }
        String description = request.description() == null ? null : request.description().trim();
        UUID categoryId = request.categoryId();
        if (categoryId != null && categories.findById(categoryId).isEmpty()) {
            throw new IllegalArgumentException("Unknown category id: " + categoryId);
        }
        services.update(id, name, slug, description, request.durationMinutes(), price, request.bufferMinutes(), categoryId);
        return services.findById(id).orElseThrow(() -> new ServiceNotFoundException(id));
    }

    private static String slugify(String name) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "service-" + UUID.randomUUID().toString().substring(0, 8) : slug;
    }
}
