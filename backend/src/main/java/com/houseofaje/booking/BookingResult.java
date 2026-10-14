package com.houseofaje.booking;

import com.houseofaje.payment.DepositInstructions;

public record BookingResult(Booking booking, DepositInstructions depositInstructions) {}
