package com.example.pcloudbackrest;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CronScheduleTest {
    @Test
    void calculatesNextDailyRun() {
        CronSchedule schedule = CronSchedule.parse("30 2 * * *", ZoneId.of("Europe/Copenhagen"));
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 3, 1, 0, 0, 0, ZoneId.of("Europe/Copenhagen"));

        assertEquals(
                ZonedDateTime.of(2026, 5, 3, 2, 30, 0, 0, ZoneId.of("Europe/Copenhagen")),
                schedule.nextAfter(now)
        );
    }

    @Test
    void supportsWeekdayNames() {
        CronSchedule schedule = CronSchedule.parse("0 8 * * MON", ZoneId.of("UTC"));
        ZonedDateTime sunday = ZonedDateTime.of(2026, 5, 3, 20, 0, 0, 0, ZoneId.of("UTC"));

        assertEquals(
                ZonedDateTime.of(2026, 5, 4, 8, 0, 0, 0, ZoneId.of("UTC")),
                schedule.nextAfter(sunday)
        );
    }

    @Test
    void supportsSteps() {
        CronSchedule schedule = CronSchedule.parse("*/15 * * * *", ZoneId.of("UTC"));
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 3, 8, 7, 0, 0, ZoneId.of("UTC"));

        assertEquals(
                ZonedDateTime.of(2026, 5, 3, 8, 15, 0, 0, ZoneId.of("UTC")),
                schedule.nextAfter(now)
        );
    }

    @Test
    void rejectsInvalidExpression() {
        assertThrows(IllegalArgumentException.class, () -> CronSchedule.parse("0 8 *", ZoneId.of("UTC")));
        assertThrows(IllegalArgumentException.class, () -> CronSchedule.parse("61 8 * * *", ZoneId.of("UTC")));
    }
}
