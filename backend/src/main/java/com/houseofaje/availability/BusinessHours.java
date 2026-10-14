package com.houseofaje.availability;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Fixed business opening hours (product rule).
 * Mon-Fri 09:00-21:00, Sat 08:00-22:00, Sun 12:00-19:00.
 */
@Component
public class BusinessHours {

    private static final Map<DayOfWeek, WorkPeriod> HOURS = Map.of(
            DayOfWeek.MONDAY, new WorkPeriod(LocalTime.of(9, 0), LocalTime.of(21, 0)),
            DayOfWeek.TUESDAY, new WorkPeriod(LocalTime.of(9, 0), LocalTime.of(21, 0)),
            DayOfWeek.WEDNESDAY, new WorkPeriod(LocalTime.of(9, 0), LocalTime.of(21, 0)),
            DayOfWeek.THURSDAY, new WorkPeriod(LocalTime.of(9, 0), LocalTime.of(21, 0)),
            DayOfWeek.FRIDAY, new WorkPeriod(LocalTime.of(9, 0), LocalTime.of(21, 0)),
            DayOfWeek.SATURDAY, new WorkPeriod(LocalTime.of(8, 0), LocalTime.of(22, 0)),
            DayOfWeek.SUNDAY, new WorkPeriod(LocalTime.of(12, 0), LocalTime.of(19, 0)));

    public Optional<WorkPeriod> hoursFor(DayOfWeek day) {
        return Optional.ofNullable(HOURS.get(day));
    }

    public record WorkPeriod(LocalTime start, LocalTime end) {}
}
