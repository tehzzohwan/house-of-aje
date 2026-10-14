package com.houseofaje.staff;

import java.util.List;
import java.util.UUID;

/** Full replacement of a staff member's services and working hours. */
public record SaveStaffAvailabilityRequest(
        List<UUID> serviceIds,
        List<StaffHours> hours) {}
