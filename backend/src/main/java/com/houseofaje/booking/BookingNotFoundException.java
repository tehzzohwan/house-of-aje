package com.houseofaje.booking;

import java.util.UUID;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(UUID id) {
        super("Booking not found: " + id);
    }

    public BookingNotFoundException(String reference) {
        super("Booking not found: " + reference);
    }
}
