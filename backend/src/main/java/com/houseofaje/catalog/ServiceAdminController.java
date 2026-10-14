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

/** Admin service management: list, create, and toggle a service's availability. */
@RestController
@RequestMapping("/api/admin/services")
public class ServiceAdminController {

    private final ServiceAdminService services;

    public ServiceAdminController(ServiceAdminService services) {
        this.services = services;
    }

    @GetMapping
    public List<AdminService> list() {
        return services.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminService create(@RequestBody CreateServiceRequest request) {
        return services.create(request);
    }

    @PutMapping("/{id}")
    public AdminService update(@PathVariable UUID id, @RequestBody CreateServiceRequest request) {
        return services.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public AdminService activate(@PathVariable UUID id) {
        return services.setActive(id, true);
    }

    @PostMapping("/{id}/deactivate")
    public AdminService deactivate(@PathVariable UUID id) {
        return services.setActive(id, false);
    }
}
