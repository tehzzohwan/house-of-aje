package com.houseofaje.review;

import java.util.UUID;

public class ReviewAlreadyExistsException extends RuntimeException {
    public ReviewAlreadyExistsException(UUID bookingId) {
        super("A review already exists for booking " + bookingId);
    }
}
