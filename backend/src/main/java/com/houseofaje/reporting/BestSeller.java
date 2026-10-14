package com.houseofaje.reporting;

import java.math.BigDecimal;

/** Services ranked by completed-booking revenue. */
public record BestSeller(String serviceName, long completed, BigDecimal revenue) {}
