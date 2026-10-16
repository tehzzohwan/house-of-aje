package com.houseofaje.review;

import java.util.List;

/** Latest published reviews plus the aggregate rating, for the public landing page. */
public record ReviewSummary(List<PublishedReview> reviews, double averageRating, long count) {}
