package com.houseofaje.reporting;

import java.math.BigDecimal;

/** Bookings handled, completed/cancelled breakdown, and paid revenue per staff member. */
public record StaffPerformance(
        String staffName,
        long bookings,
        long completed,
        long cancelled,
        BigDecimal revenue) {}
