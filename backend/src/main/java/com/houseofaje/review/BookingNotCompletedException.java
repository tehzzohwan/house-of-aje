package com.houseofaje.review;

import com.houseofaje.booking.BookingStatus;
import java.util.UUID;

public class BookingNotCompletedException extends RuntimeException {
    public BookingNotCompletedException(UUID bookingId, BookingStatus status) {
        super("Booking " + bookingId + " is " + status + ", not COMPLETED");
    }
}
