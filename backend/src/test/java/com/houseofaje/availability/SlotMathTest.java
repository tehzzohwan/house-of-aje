package com.houseofaje.availability;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlotMathTest {

    private static TimeInterval ti(String start, String end) {
        return new TimeInterval(LocalDateTime.parse(start), LocalDateTime.parse(end));
    }

    private static LocalDateTime at(String s) {
        return LocalDateTime.parse(s);
    }

    // ---- normalize ----

    @Test
    void normalizeMergesOverlappingAndAdjacent() {
        List<TimeInterval> in = List.of(
                ti("2026-01-12T10:00", "2026-01-12T11:00"),
                ti("2026-01-12T09:00", "2026-01-12T09:30"),
                ti("2026-01-12T09:30", "2026-01-12T10:00"), // adjacent to previous
                ti("2026-01-12T10:30", "2026-01-12T11:30")); // overlaps first

        assertThat(SlotMath.normalize(in))
                .containsExactly(ti("2026-01-12T09:00", "2026-01-12T11:30"));
    }

    @Test
    void normalizeKeepsDisjoint() {
        List<TimeInterval> in = List.of(
                ti("2026-01-12T11:00", "2026-01-12T12:00"),
                ti("2026-01-12T09:00", "2026-01-12T10:00"));

        assertThat(SlotMath.normalize(in))
                .containsExactly(
                        ti("2026-01-12T09:00", "2026-01-12T10:00"),
                        ti("2026-01-12T11:00", "2026-01-12T12:00"));
    }

    // ---- intersect ----

    @Test
    void intersectDisjointIsEmpty() {
        assertThat(SlotMath.intersect(
                List.of(ti("2026-01-12T09:00", "2026-01-12T10:00")),
                List.of(ti("2026-01-12T11:00", "2026-01-12T12:00"))))
                .isEmpty();
    }

    @Test
    void intersectPartialOverlap() {
        assertThat(SlotMath.intersect(
                List.of(ti("2026-01-12T09:00", "2026-01-12T12:00")),
                List.of(ti("2026-01-12T10:00", "2026-01-12T13:00"))))
                .containsExactly(ti("2026-01-12T10:00", "2026-01-12T12:00"));
    }

    @Test
    void intersectContainment() {
        assertThat(SlotMath.intersect(
                List.of(ti("2026-01-12T09:00", "2026-01-12T18:00")),
                List.of(ti("2026-01-12T10:00", "2026-01-12T11:00"))))
                .containsExactly(ti("2026-01-12T10:00", "2026-01-12T11:00"));
    }

    @Test
    void intersectAdjacentIsEmpty() {
        // half-open [9,10) and [10,11) touch but do not overlap
        assertThat(SlotMath.intersect(
                List.of(ti("2026-01-12T09:00", "2026-01-12T10:00")),
                List.of(ti("2026-01-12T10:00", "2026-01-12T11:00"))))
                .isEmpty();
    }

    // ---- subtract ----

    @Test
    void subtractBlockedInsideSplits() {
        assertThat(SlotMath.subtract(
                List.of(ti("2026-01-12T09:00", "2026-01-12T18:00")),
                List.of(ti("2026-01-12T10:00", "2026-01-12T11:00"))))
                .containsExactly(
                        ti("2026-01-12T09:00", "2026-01-12T10:00"),
                        ti("2026-01-12T11:00", "2026-01-12T18:00"));
    }

    @Test
    void subtractBlockedCoversAll() {
        assertThat(SlotMath.subtract(
                List.of(ti("2026-01-12T09:00", "2026-01-12T10:00")),
                List.of(ti("2026-01-12T09:00", "2026-01-12T10:00"))))
                .isEmpty();
    }

    @Test
    void subtractBlockedAtStart() {
        assertThat(SlotMath.subtract(
                List.of(ti("2026-01-12T09:00", "2026-01-12T10:00")),
                List.of(ti("2026-01-12T09:00", "2026-01-12T09:30"))))
                .containsExactly(ti("2026-01-12T09:30", "2026-01-12T10:00"));
    }

    @Test
    void subtractBlockedAtEnd() {
        assertThat(SlotMath.subtract(
                List.of(ti("2026-01-12T09:00", "2026-01-12T10:00")),
                List.of(ti("2026-01-12T09:30", "2026-01-12T10:00"))))
                .containsExactly(ti("2026-01-12T09:00", "2026-01-12T09:30"));
    }

    @Test
    void subtractMultipleBlocked() {
        assertThat(SlotMath.subtract(
                List.of(ti("2026-01-12T09:00", "2026-01-12T18:00")),
                List.of(ti("2026-01-12T10:00", "2026-01-12T11:00"), ti("2026-01-12T14:00", "2026-01-12T15:00"))))
                .containsExactly(
                        ti("2026-01-12T09:00", "2026-01-12T10:00"),
                        ti("2026-01-12T11:00", "2026-01-12T14:00"),
                        ti("2026-01-12T15:00", "2026-01-12T18:00"));
    }

    // ---- availableSlotStarts ----

    @Test
    void generatesSlotsAtStepWithinWorkingWindow() {
        var working = List.of(ti("2026-01-12T09:00", "2026-01-12T10:00"));
        var starts = SlotMath.availableSlotStarts(working, List.of(),
                Duration.ofMinutes(30), Duration.ZERO, Duration.ofMinutes(15));

        assertThat(starts).containsExactly(
                at("2026-01-12T09:00"),
                at("2026-01-12T09:15"),
                at("2026-01-12T09:30"));
    }

    @Test
    void existingBookingBufferBlocksFollowingSlots() {
        var working = List.of(ti("2026-01-12T09:00", "2026-01-12T14:00"));
        // existing booking 10:00-11:00 with 15m buffer -> occupied [10:00, 11:15)
        var occupied = List.of(ti("2026-01-12T10:00", "2026-01-12T11:15"));
        // new service: 60m duration, no buffer
        var starts = SlotMath.availableSlotStarts(working, occupied,
                Duration.ofMinutes(60), Duration.ZERO, Duration.ofMinutes(15));

        assertThat(starts).contains(at("2026-01-12T09:00"));
        assertThat(starts).doesNotContain(at("2026-01-12T09:15"));
        assertThat(starts).doesNotContain(at("2026-01-12T11:00"));
        assertThat(starts).contains(at("2026-01-12T11:15"));
    }

    @Test
    void newBookingTrailingBufferBlocksSlotCollidingWithNextBooking() {
        var working = List.of(ti("2026-01-12T09:00", "2026-01-12T14:00"));
        // existing booking 11:00-12:00 with no buffer -> occupied [11:00, 12:00)
        var occupied = List.of(ti("2026-01-12T11:00", "2026-01-12T12:00"));
        // new service: 60m duration WITH 15m buffer
        var starts = SlotMath.availableSlotStarts(working, occupied,
                Duration.ofMinutes(60), Duration.ofMinutes(15), Duration.ofMinutes(15));

        // 09:45 -> [09:45,11:00) + buffer [11:00,11:15): buffer ends exactly at booking start -> OK
        assertThat(starts).contains(at("2026-01-12T09:45"));
        // 10:00 -> [10:00,11:00) + buffer [11:00,11:15) collides with the 11:00 booking -> blocked
        assertThat(starts).doesNotContain(at("2026-01-12T10:00"));
        // 12:00 -> clear of the booking
        assertThat(starts).contains(at("2026-01-12T12:00"));
    }

    @Test
    void durationLongerThanWindowYieldsNoSlots() {
        var working = List.of(ti("2026-01-12T09:00", "2026-01-12T10:00"));
        assertThat(SlotMath.availableSlotStarts(working, List.of(),
                Duration.ofMinutes(120), Duration.ZERO, Duration.ofMinutes(15)))
                .isEmpty();
    }

    @Test
    void emptyWorkingYieldsNoSlots() {
        assertThat(SlotMath.availableSlotStarts(List.of(), List.of(),
                Duration.ofMinutes(60), Duration.ZERO, Duration.ofMinutes(15)))
                .isEmpty();
    }
}
