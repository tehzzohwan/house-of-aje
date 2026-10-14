package com.houseofaje.reporting;

import java.math.BigDecimal;

/** Money collected (deposits + in-person balances) minus refunds issued. */
public record ProfitReport(BigDecimal grossRevenue, BigDecimal refunds, BigDecimal netProfit) {}
