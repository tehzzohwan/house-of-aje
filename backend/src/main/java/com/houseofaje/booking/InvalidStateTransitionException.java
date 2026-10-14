package com.houseofaje.booking;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(BookingStatus from, BookingStatus to) {
        super("Illegal booking transition: " + from + " -> " + to);
    }
}
