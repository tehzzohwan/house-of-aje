package com.houseofaje.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin management of service categories. */
@Service
public class ServiceCategoryAdminService {

    private final ServiceCategoryRepository categories;

    public ServiceCategoryAdminService(ServiceCategoryRepository categories) {
        this.categories = categories;
    }

    public List<ServiceCategory> list() {
        return categories.findAll();
    }

    @Transactional
    public ServiceCategory create(CreateServiceCategoryRequest request) {
        String name = request.name() == null ? null : request.name().trim();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        String slug = request.slug() == null || request.slug().isBlank()
                ? slugify(name)
                : request.slug().trim();
        if (categories.slugExists(slug)) {
            throw new IllegalArgumentException("A category with this name already exists");
        }
        UUID id = UUID.randomUUID();
        categories.insert(id, name, slug, true);
        return categories.findById(id).orElseThrow(() -> new ServiceCategoryNotFoundException(id));
    }

    @Transactional
    public ServiceCategory setActive(UUID id, boolean active) {
        if (!categories.setActive(id, active)) {
            throw new ServiceCategoryNotFoundException(id);
        }
        return categories.findById(id).orElseThrow(() -> new ServiceCategoryNotFoundException(id));
    }

    @Transactional
    public ServiceCategory update(UUID id, CreateServiceCategoryRequest request) {
        if (categories.findById(id).isEmpty()) {
            throw new ServiceCategoryNotFoundException(id);
        }
        String name = request.name() == null ? null : request.name().trim();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        String slug = request.slug() == null || request.slug().isBlank()
                ? slugify(name)
                : request.slug().trim();
        if (categories.slugExists(slug, id)) {
            throw new IllegalArgumentException("A category with this name already exists");
        }
        categories.update(id, name, slug);
        return categories.findById(id).orElseThrow(() -> new ServiceCategoryNotFoundException(id));
    }

    private static String slugify(String name) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "category-" + UUID.randomUUID().toString().substring(0, 8) : slug;
    }
}
