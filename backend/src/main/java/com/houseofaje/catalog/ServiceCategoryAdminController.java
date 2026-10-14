package com.houseofaje.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin management of service categories. */
@RestController
@RequestMapping("/api/admin/categories")
public class ServiceCategoryAdminController {

    private final ServiceCategoryAdminService categories;

    public ServiceCategoryAdminController(ServiceCategoryAdminService categories) {
        this.categories = categories;
    }

    @GetMapping
    public List<ServiceCategory> list() {
        return categories.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceCategory create(@RequestBody CreateServiceCategoryRequest request) {
        return categories.create(request);
    }

    @PutMapping("/{id}")
    public ServiceCategory update(@PathVariable UUID id, @RequestBody CreateServiceCategoryRequest request) {
        return categories.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public ServiceCategory activate(@PathVariable UUID id) {
        return categories.setActive(id, true);
    }

    @PostMapping("/{id}/deactivate")
    public ServiceCategory deactivate(@PathVariable UUID id) {
        return categories.setActive(id, false);
    }
}
