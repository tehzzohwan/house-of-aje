package com.houseofaje.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public service catalogue, used by the booking flow. */
@RestController
@RequestMapping("/api/public/services")
public class ServiceController {

    private final ServiceRepository services;

    public ServiceController(ServiceRepository services) {
        this.services = services;
    }

    @GetMapping
    public List<Service> list() {
        return services.findActive();
    }
}
