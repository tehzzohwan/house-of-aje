package com.houseofaje.staff;

import com.houseofaje.availability.AvailabilityService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public staff catalogue used by the booking flow. */
@RestController
@RequestMapping("/api/public/staff")
public class StaffCatalogController {

    private final StaffRepository staff;
    private final StaffScheduleRepository schedule;
    private final AvailabilityService availability;

    public StaffCatalogController(StaffRepository staff, StaffScheduleRepository schedule,
            AvailabilityService availability) {
        this.staff = staff;
        this.schedule = schedule;
        this.availability = availability;
    }

    @GetMapping
    public List<PublicStaff> list(
            @RequestParam UUID serviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return staff.findPerformingService(serviceId).stream()
                .map(s -> new PublicStaff(
                        s.id(), s.name(), s.bio(), s.photoUrl(),
                        schedule.findServiceNames(s.id()),
                        date != null && !availability.availableSlots(serviceId, s.id(), date).isEmpty()))
                .toList();
    }
}
