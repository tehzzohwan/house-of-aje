package com.houseofaje.booking;

import java.time.Instant;
import java.util.UUID;

public record CreateBookingRequest(
        UUID customerId,
        UUID serviceId,
        UUID staffId,
        Instant startAt,
        boolean payFull) {}
