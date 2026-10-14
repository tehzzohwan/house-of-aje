package com.houseofaje.staff;

import java.util.List;
import java.util.UUID;

/** A staff member's schedule: which services they perform and their weekly hours. */
public record StaffAvailability(
        UUID staffId,
        List<UUID> serviceIds,
        List<StaffHours> hours) {}
