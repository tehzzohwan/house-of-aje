package com.houseofaje.staff;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads and writes the staff attendance (clock in / clock out) table. */
@Repository
public class StaffAttendanceRepository {

    private final JdbcTemplate jdbc;

    public StaffAttendanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isClockedIn(UUID staffId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM staff_attendance WHERE staff_id = ? AND clock_out_at IS NULL",
                Integer.class, staffId);
        return count != null && count > 0;
    }

    public Optional<Instant> openClockIn(UUID staffId) {
        return jdbc.query(
                "SELECT clock_in_at FROM staff_attendance WHERE staff_id = ? AND clock_out_at IS NULL "
                        + "ORDER BY clock_in_at DESC LIMIT 1",
                (rs, n) -> rs.getObject("clock_in_at", OffsetDateTime.class).toInstant(),
                staffId).stream().findFirst();
    }

    public void clockIn(UUID staffId, Instant at) {
        jdbc.update("INSERT INTO staff_attendance (staff_id, clock_in_at) VALUES (?, ?)",
                staffId, OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
    }

    /** Closes the open shift, if any, at {@code at}. Returns true when a row was closed. */
    public boolean clockOut(UUID staffId, Instant at) {
        return jdbc.update(
                "UPDATE staff_attendance SET clock_out_at = ? WHERE staff_id = ? AND clock_out_at IS NULL",
                OffsetDateTime.ofInstant(at, ZoneOffset.UTC), staffId) == 1;
    }

    public List<OpenShift> findOpenShifts() {
        return jdbc.query(
                "SELECT staff_id, clock_in_at FROM staff_attendance WHERE clock_out_at IS NULL",
                (rs, n) -> new OpenShift(
                        rs.getObject("staff_id", UUID.class),
                        rs.getObject("clock_in_at", OffsetDateTime.class).toInstant()));
    }

    /** Attendance intervals overlapping {@code (from, to]}; open shifts come back with a null end. */
    public List<Interval> intervals(UUID staffId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT clock_in_at, clock_out_at
                FROM staff_attendance
                WHERE staff_id = ?
                  AND clock_in_at < ?
                  AND (clock_out_at IS NULL OR clock_out_at > ?)
                ORDER BY clock_in_at
                """,
                (rs, n) -> {
                    OffsetDateTime out = rs.getObject("clock_out_at", OffsetDateTime.class);
                    return new Interval(
                            rs.getObject("clock_in_at", OffsetDateTime.class).toInstant(),
                            out == null ? null : out.toInstant());
                },
                staffId,
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC));
    }

    public record OpenShift(UUID staffId, Instant clockInAt) {}

    public record Interval(Instant clockInAt, Instant clockOutAt) {}
}
