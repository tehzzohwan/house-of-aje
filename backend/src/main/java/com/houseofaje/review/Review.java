package com.houseofaje.review;

import java.time.Instant;
import java.util.UUID;

public record Review(
        UUID id,
        UUID bookingId,
        UUID customerId,
        int rating,
        String comment,
        ReviewStatus status,
        Instant createdAt) {}
