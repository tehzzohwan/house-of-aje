package com.houseofaje.availability;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure interval arithmetic for availability. No I/O, fully unit-testable.
 *
 * <p>The buffer rule ("gap after the appointment before the next can start") is
 * modelled as each appointment occupying {@code [start, end + buffer)}. Two
 * appointments coexist iff their occupied intervals are disjoint. When
 * generating slots for a new service, a candidate start {@code s} is valid iff
 * {@code [s, s+duration+buffer)} is disjoint from every existing occupied interval
 * and {@code [s, s+duration)} lies inside a working window.</p>
 */
public final class SlotMath {

    private SlotMath() {}

    /** Sort by start and merge overlapping or adjacent intervals. */
    public static List<TimeInterval> normalize(List<TimeInterval> intervals) {
        List<TimeInterval> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparing(TimeInterval::start));
        List<TimeInterval> out = new ArrayList<>();
        for (TimeInterval i : sorted) {
            if (out.isEmpty()) {
                out.add(i);
                continue;
            }
            TimeInterval last = out.get(out.size() - 1);
            if (!i.start().isAfter(last.end())) { // overlapping or adjacent -> merge
                LocalDateTime end = last.end().isAfter(i.end()) ? last.end() : i.end();
                out.set(out.size() - 1, new TimeInterval(last.start(), end));
            } else {
                out.add(i);
            }
        }
        return out;
    }

    /** Intersection of two sets of intervals. */
    public static List<TimeInterval> intersect(List<TimeInterval> a, List<TimeInterval> b) {
        a = normalize(a);
        b = normalize(b);
        List<TimeInterval> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < a.size() && j < b.size()) {
            TimeInterval x = a.get(i);
            TimeInterval y = b.get(j);
            LocalDateTime start = x.start().isAfter(y.start()) ? x.start() : y.start();
            LocalDateTime end = x.end().isBefore(y.end()) ? x.end() : y.end();
            if (start.isBefore(end)) {
                out.add(new TimeInterval(start, end));
            }
            if (x.end().isBefore(y.end())) {
                i++;
            } else {
                j++;
            }
        }
        return out;
    }

    /** Subtract {@code blocked} intervals from {@code base}. */
    public static List<TimeInterval> subtract(List<TimeInterval> base, List<TimeInterval> blocked) {
        base = normalize(base);
        blocked = normalize(blocked);
        List<TimeInterval> out = new ArrayList<>();
        for (TimeInterval b : base) {
            LocalDateTime cursor = b.start();
            for (TimeInterval blk : blocked) {
                if (!blk.end().isAfter(cursor)) {
                    continue; // blocked interval lies entirely before the cursor
                }
                if (!blk.start().isBefore(b.end())) {
                    break; // blocked intervals are sorted; the rest start after this base interval
                }
                if (blk.start().isAfter(cursor)) {
                    out.add(new TimeInterval(cursor, blk.start()));
                }
                if (blk.end().isAfter(cursor)) {
                    cursor = blk.end();
                }
            }
            if (cursor.isBefore(b.end())) {
                out.add(new TimeInterval(cursor, b.end()));
            }
        }
        return out;
    }

    /**
     * Candidate slot starts within {@code working}, stepping by {@code step}, that
     * do not collide with {@code occupied} once the new service's own buffer is
     * applied. Both input lists are normalized internally.
     */
    public static List<LocalDateTime> availableSlotStarts(
            List<TimeInterval> working,
            List<TimeInterval> occupied,
            Duration duration,
            Duration buffer,
            Duration step) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive: " + duration);
        }
        if (step.isZero() || step.isNegative()) {
            throw new IllegalArgumentException("step must be positive: " + step);
        }
        working = normalize(working);
        occupied = normalize(occupied);

        List<LocalDateTime> starts = new ArrayList<>();
        for (TimeInterval w : working) {
            LocalDateTime s = w.start();
            LocalDateTime latest = w.end().minus(duration);
            while (!s.isAfter(latest)) {
                LocalDateTime appointmentEnd = s.plus(duration);
                LocalDateTime occupiedEnd = appointmentEnd.plus(buffer);
                if (!intersectsAny(occupied, s, occupiedEnd)) {
                    starts.add(s);
                }
                s = s.plus(step);
            }
        }
        return starts;
    }

    private static boolean intersectsAny(List<TimeInterval> occupied, LocalDateTime start, LocalDateTime end) {
        for (TimeInterval o : occupied) {
            if (start.isBefore(o.end()) && o.start().isBefore(end)) {
                return true;
            }
        }
        return false;
    }
}
