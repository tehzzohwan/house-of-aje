package com.houseofaje.staff;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads and replaces the schedule tables that drive availability. */
@Repository
public class StaffScheduleRepository {

    private final JdbcTemplate jdbc;

    public StaffScheduleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UUID> findServiceIds(UUID staffId) {
        return jdbc.queryForList(
                "SELECT service_id FROM staff_services WHERE staff_id = ?", UUID.class, staffId);
    }

    public List<String> findServiceNames(UUID staffId) {
        return jdbc.queryForList("""
                SELECT s.name
                FROM staff_services ss
                JOIN services s ON s.id = ss.service_id
                WHERE ss.staff_id = ?
                ORDER BY s.name
                """, String.class, staffId);
    }

    public List<StaffHours> findHours(UUID staffId) {
        return jdbc.query(
                "SELECT day_of_week, start_time, end_time FROM staff_working_hours WHERE staff_id = ? "
                        + "ORDER BY day_of_week, start_time",
                (rs, n) -> new StaffHours(
                        rs.getInt("day_of_week"),
                        rs.getTime("start_time").toLocalTime(),
                        rs.getTime("end_time").toLocalTime()),
                staffId);
    }

    public void replaceServices(UUID staffId, List<UUID> serviceIds) {
        jdbc.update("DELETE FROM staff_services WHERE staff_id = ?", staffId);
        for (UUID serviceId : serviceIds) {
            jdbc.update("INSERT INTO staff_services (staff_id, service_id) VALUES (?, ?)", staffId, serviceId);
        }
    }

    /** The latest end time in the staff's schedule for a weekday (auto clock-out). */
    public Optional<LocalTime> findLatestEnd(UUID staffId, int dayOfWeek) {
        LocalTime end = jdbc.queryForObject(
                "SELECT MAX(end_time) FROM staff_working_hours WHERE staff_id = ? AND day_of_week = ?",
                (rs, n) -> rs.getTime(1) == null ? null : rs.getTime(1).toLocalTime(),
                staffId, dayOfWeek);
        return Optional.ofNullable(end);
    }

    public void replaceHours(UUID staffId, List<StaffHours> hours) {
        jdbc.update("DELETE FROM staff_working_hours WHERE staff_id = ?", staffId);
        for (StaffHours h : hours) {
            jdbc.update(
                    "INSERT INTO staff_working_hours (staff_id, day_of_week, start_time, end_time) VALUES (?, ?, ?, ?)",
                    staffId, h.dayOfWeek(), h.startTime(), h.endTime());
        }
    }
}
