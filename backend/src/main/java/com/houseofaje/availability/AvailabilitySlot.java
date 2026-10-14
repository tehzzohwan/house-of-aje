package com.houseofaje.availability;

import java.time.Instant;
import java.util.UUID;

/**
 * A bookable time slot. Times are returned as UTC instants; the client converts
 * to its own timezone for display.
 */
public record AvailabilitySlot(Instant start, Instant end, UUID staffId, String staffName) {}
