package com.houseofaje.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Booking(
        UUID id,
        String reference,
        UUID customerId,
        UUID serviceId,
        UUID staffId,
        Instant startAt,
        Instant endAt,
        BookingStatus status,
        BigDecimal price,
        BigDecimal depositAmount,
        Instant createdAt) {}
