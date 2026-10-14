package com.houseofaje.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Payment(
        UUID id,
        UUID bookingId,
        BigDecimal amount,
        PaymentType type,
        PaymentStatus status,
        String provider,
        String providerReference,
        String providerTransactionReference,
        String idempotencyKey,
        String currency,
        Instant paidAt) {}
