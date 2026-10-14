package com.houseofaje.staff;

import com.houseofaje.catalog.ServiceRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin management of a staff member's services and weekly working hours. */
@Service
public class StaffAvailabilityService {

    private final StaffRepository staff;
    private final StaffScheduleRepository schedule;
    private final ServiceRepository services;

    public StaffAvailabilityService(StaffRepository staff, StaffScheduleRepository schedule, ServiceRepository services) {
        this.staff = staff;
        this.schedule = schedule;
        this.services = services;
    }

    public StaffAvailability get(UUID staffId) {
        requireStaff(staffId);
        return new StaffAvailability(staffId, schedule.findServiceIds(staffId), schedule.findHours(staffId));
    }

    @Transactional
    public StaffAvailability save(UUID staffId, SaveStaffAvailabilityRequest request) {
        requireStaff(staffId);
        List<UUID> serviceIds = validateServices(request.serviceIds());
        List<StaffHours> hours = validateHours(request.hours());
        schedule.replaceServices(staffId, serviceIds);
        schedule.replaceHours(staffId, hours);
        return new StaffAvailability(staffId, serviceIds, hours);
    }

    private void requireStaff(UUID staffId) {
        if (staff.findById(staffId).isEmpty()) {
            throw new StaffNotFoundException(staffId);
        }
    }

    private List<UUID> validateServices(List<UUID> serviceIds) {
        if (serviceIds == null) {
            return List.of();
        }
        List<UUID> unique = serviceIds.stream().filter(Objects::nonNull).distinct().toList();
        for (UUID id : unique) {
            if (services.findById(id).isEmpty()) {
                throw new IllegalArgumentException("Unknown service id: " + id);
            }
        }
        return unique;
    }

    private List<StaffHours> validateHours(List<StaffHours> hours) {
        if (hours == null) {
            return List.of();
        }
        List<StaffHours> result = new ArrayList<>();
        Set<Integer> seenDays = new HashSet<>();
        for (StaffHours h : hours) {
            if (h == null) {
                continue;
            }
            if (h.dayOfWeek() < 1 || h.dayOfWeek() > 7) {
                throw new IllegalArgumentException("dayOfWeek must be between 1 and 7");
            }
            if (h.startTime() == null || h.endTime() == null) {
                throw new IllegalArgumentException("startTime and endTime are required");
            }
            if (!h.startTime().isBefore(h.endTime())) {
                throw new IllegalArgumentException("startTime must be before endTime");
            }
            if (!seenDays.add(h.dayOfWeek())) {
                throw new IllegalArgumentException("Duplicate hours for dayOfWeek " + h.dayOfWeek());
            }
            result.add(h);
        }
        return result;
    }
}
