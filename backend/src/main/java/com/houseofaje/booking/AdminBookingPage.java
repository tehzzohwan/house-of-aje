package com.houseofaje.booking;

import java.util.List;

/** A page of admin bookings plus the total count (for pagination). */
public record AdminBookingPage(List<AdminBooking> items, long total) {}
