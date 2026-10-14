package com.houseofaje.staff;

import java.time.LocalTime;

/** One day's working hours. dayOfWeek is ISO: 1=Monday .. 7=Sunday. */
public record StaffHours(int dayOfWeek, LocalTime startTime, LocalTime endTime) {}
