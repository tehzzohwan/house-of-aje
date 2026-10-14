package com.houseofaje.review;

import java.util.UUID;

public record CreateReviewRequest(UUID customerId, UUID bookingId, int rating, String comment) {}
