package com.houseofaje.booking;

import com.houseofaje.payment.PaymentState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A booking joined with its customer, service and staff names, for the admin view. */
public record AdminBooking(
        UUID id,
        String reference,
        String customerName,
        String serviceName,
        String staffName,
        Instant startAt,
        Instant endAt,
        BookingStatus status,
        BigDecimal price,
        BigDecimal depositAmount,
        PaymentState paymentState) {}
