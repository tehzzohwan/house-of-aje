package com.houseofaje.staff;

import com.houseofaje.booking.BookingSummary;
import java.util.List;
import java.util.UUID;

/** A staff member's self-service profile view. */
public record StaffProfile(
        UUID id,
        String name,
        String email,
        String phone,
        String bio,
        String photoUrl,
        boolean active,
        List<String> services,
        List<StaffHours> hours,
        List<BookingSummary> bookings,
        boolean clockedIn,
        long workedMinutesToday) {}
