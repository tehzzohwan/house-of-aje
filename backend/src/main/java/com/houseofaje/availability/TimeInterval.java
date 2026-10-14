package com.houseofaje.availability;

import java.time.LocalDateTime;

/**
 * A half-open interval {@code [start, end)}. Immutable.
 * All availability math operates on these in the business's local timezone.
 */
public record TimeInterval(LocalDateTime start, LocalDateTime end) {

    public TimeInterval {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end: " + start + " -> " + end);
        }
    }

    public boolean overlaps(TimeInterval other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
