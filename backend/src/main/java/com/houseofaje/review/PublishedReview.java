package com.houseofaje.review;

import java.time.Instant;
import java.util.UUID;

/** A review as shown to the public (customer name, no moderation state). */
public record PublishedReview(UUID id, String customerName, int rating, String comment, Instant createdAt) {}
