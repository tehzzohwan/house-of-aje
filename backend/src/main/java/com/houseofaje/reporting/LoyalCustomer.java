package com.houseofaje.reporting;

import java.util.UUID;

/** Customers ranked by number of completed bookings. */
public record LoyalCustomer(UUID customerId, String name, String email, long bookings) {}
