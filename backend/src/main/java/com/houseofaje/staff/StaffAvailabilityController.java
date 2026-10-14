package com.houseofaje.staff;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin endpoint to view and replace a staff member's services and working hours. */
@RestController
@RequestMapping("/api/admin/staff/{id}/availability")
public class StaffAvailabilityController {

    private final StaffAvailabilityService availability;

    public StaffAvailabilityController(StaffAvailabilityService availability) {
        this.availability = availability;
    }

    @GetMapping
    public StaffAvailability get(@PathVariable UUID id) {
        return availability.get(id);
    }

    @PutMapping
    public StaffAvailability save(@PathVariable UUID id, @RequestBody SaveStaffAvailabilityRequest request) {
        return availability.save(id, request);
    }
}
