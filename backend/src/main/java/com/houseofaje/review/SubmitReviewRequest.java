package com.houseofaje.review;

/** A review submitted by the logged-in customer; the customer and booking are derived from the JWT. */
public record SubmitReviewRequest(int rating, String comment) {}
