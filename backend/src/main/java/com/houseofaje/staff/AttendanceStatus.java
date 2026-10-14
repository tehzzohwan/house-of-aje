package com.houseofaje.staff;

import java.time.Instant;

/** The logged-in staff member's current clock state. */
public record AttendanceStatus(boolean clockedIn, Instant clockedInAt) {}
