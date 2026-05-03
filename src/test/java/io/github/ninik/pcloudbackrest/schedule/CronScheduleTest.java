package io.github.ninik.pcloudbackrest.schedule;

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
    void supportsRanges() {
        CronSchedule schedule = CronSchedule.parse("0 9-11 * * *", ZoneId.of("UTC"));
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 3, 8, 30, 0, 0, ZoneId.of("UTC"));

        assertEquals(
                ZonedDateTime.of(2026, 5, 3, 9, 0, 0, 0, ZoneId.of("UTC")),
                schedule.nextAfter(now)
        );
    }

    @Test
    void supportsMonthNamesAndSundaySeven() {
        CronSchedule schedule = CronSchedule.parse("0 9 * JAN SUN", ZoneId.of("UTC"));
        ZonedDateTime now = ZonedDateTime.of(2026, 1, 3, 10, 0, 0, 0, ZoneId.of("UTC"));

        assertEquals(
                ZonedDateTime.of(2026, 1, 4, 9, 0, 0, 0, ZoneId.of("UTC")),
                schedule.nextAfter(now)
        );

        CronSchedule sundaySeven = CronSchedule.parse("0 9 * * 7", ZoneId.of("UTC"));
        assertEquals(
                ZonedDateTime.of(2026, 1, 4, 9, 0, 0, 0, ZoneId.of("UTC")),
                sundaySeven.nextAfter(now)
        );
    }

    @Test
    void dayOfMonthAndDayOfWeekUseCronOrSemantics() {
        CronSchedule schedule = CronSchedule.parse("0 9 15 * MON", ZoneId.of("UTC"));
        ZonedDateTime sundayBeforeMonday = ZonedDateTime.of(2026, 6, 7, 10, 0, 0, 0, ZoneId.of("UTC"));

        assertEquals(
                ZonedDateTime.of(2026, 6, 8, 9, 0, 0, 0, ZoneId.of("UTC")),
                schedule.nextAfter(sundayBeforeMonday)
        );
    }

    @Test
    void dayOfWeekWildcardUsesDayOfMonthMatch() {
        CronSchedule schedule = CronSchedule.parse("0 9 15 * *", ZoneId.of("UTC"));
        ZonedDateTime beforeFifteenth = ZonedDateTime.of(2026, 6, 14, 10, 0, 0, 0, ZoneId.of("UTC"));

        assertEquals(
                ZonedDateTime.of(2026, 6, 15, 9, 0, 0, 0, ZoneId.of("UTC")),
                schedule.nextAfter(beforeFifteenth)
        );
    }

    @Test
    void impossibleScheduleThrowsWhenNoFutureRunCanBeCalculated() {
        CronSchedule impossible = CronSchedule.parse("0 0 31 FEB *", ZoneId.of("UTC"));

        assertThrows(IllegalStateException.class,
                () -> impossible.nextAfter(ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))));
    }

    @Test
    void rejectsInvalidExpression() {
        assertThrows(IllegalArgumentException.class, () -> CronSchedule.parse(null, ZoneId.of("UTC")));
        assertThrows(IllegalArgumentException.class, () -> CronSchedule.parse("   ", ZoneId.of("UTC")));
        assertThrows(IllegalArgumentException.class, () -> CronSchedule.parse("0 8 *", ZoneId.of("UTC")));
        assertThrows(IllegalArgumentException.class, () -> CronSchedule.parse("61 8 * * *", ZoneId.of("UTC")));
        assertThrows(IllegalArgumentException.class, () -> CronSchedule.parse("*/0 8 * * *", ZoneId.of("UTC")));
        assertThrows(IllegalArgumentException.class, () -> CronSchedule.parse("*/ 8 * * *", ZoneId.of("UTC")));
        assertThrows(IllegalArgumentException.class, () -> CronSchedule.parse("A 8 * * *", ZoneId.of("UTC")));
        assertThrows(IllegalArgumentException.class, () -> CronSchedule.parse(", 8 * * *", ZoneId.of("UTC")));
        assertThrows(IllegalArgumentException.class, () -> CronSchedule.parse("5-2 8 * * *", ZoneId.of("UTC")));
        assertThrows(IllegalArgumentException.class, () -> CronSchedule.parse("5- 8 * * *", ZoneId.of("UTC")));
        assertThrows(IllegalArgumentException.class, () -> CronSchedule.parse("0 8 * FOO *", ZoneId.of("UTC")));
    }
}
