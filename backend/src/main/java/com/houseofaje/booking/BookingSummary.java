package com.houseofaje.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A booking row joined with service/staff names, for customer/staff self-service views. */
public record BookingSummary(
        UUID id,
        String reference,
        String serviceName,
        String staffName,
        Instant startAt,
        BookingStatus status,
        BigDecimal price,
        BigDecimal depositAmount,
        Instant createdAt,
        boolean reviewed) {}
