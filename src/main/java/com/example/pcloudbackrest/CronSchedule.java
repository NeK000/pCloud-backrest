package com.example.pcloudbackrest;

import java.time.DayOfWeek;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class CronSchedule {
    private final String expression;
    private final Set<Integer> minutes;
    private final Set<Integer> hours;
    private final Set<Integer> daysOfMonth;
    private final Set<Integer> months;
    private final Set<Integer> daysOfWeek;
    private final boolean dayOfMonthWildcard;
    private final boolean dayOfWeekWildcard;
    private final ZoneId zoneId;

    private CronSchedule(
            String expression,
            Set<Integer> minutes,
            Set<Integer> hours,
            Set<Integer> daysOfMonth,
            Set<Integer> months,
            Set<Integer> daysOfWeek,
            boolean dayOfMonthWildcard,
            boolean dayOfWeekWildcard,
            ZoneId zoneId
    ) {
        this.expression = expression;
        this.minutes = minutes;
        this.hours = hours;
        this.daysOfMonth = daysOfMonth;
        this.months = months;
        this.daysOfWeek = daysOfWeek;
        this.dayOfMonthWildcard = dayOfMonthWildcard;
        this.dayOfWeekWildcard = dayOfWeekWildcard;
        this.zoneId = zoneId;
    }

    static CronSchedule parse(String expression, ZoneId zoneId) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("SCHEDULE_CRON must not be empty when scheduling is enabled.");
        }
        String[] fields = expression.trim().split("\\s+");
        if (fields.length != 5) {
            throw new IllegalArgumentException("SCHEDULE_CRON must use 5 fields: minute hour day-of-month month day-of-week.");
        }
        return new CronSchedule(
                expression.trim(),
                parseField(fields[0], 0, 59, null),
                parseField(fields[1], 0, 23, null),
                parseField(fields[2], 1, 31, null),
                parseField(fields[3], 1, 12, monthNames()),
                parseField(fields[4], 0, 7, dayNames()),
                "*".equals(fields[2]),
                "*".equals(fields[4]),
                zoneId
        );
    }

    ZonedDateTime nextAfter(ZonedDateTime after) {
        ZonedDateTime candidate = after.withZoneSameInstant(zoneId)
                .withSecond(0)
                .withNano(0)
                .plusMinutes(1);
        ZonedDateTime limit = candidate.plusYears(5);
        while (!candidate.isAfter(limit)) {
            if (matches(candidate)) {
                return candidate;
            }
            candidate = candidate.plusMinutes(1);
        }
        throw new IllegalStateException("Could not calculate next run for SCHEDULE_CRON=" + expression);
    }

    ZoneId zoneId() {
        return zoneId;
    }

    @Override
    public String toString() {
        return expression;
    }

    private boolean matches(ZonedDateTime candidate) {
        boolean dayOfMonthMatches = daysOfMonth.contains(candidate.getDayOfMonth());
        boolean dayOfWeekMatches = daysOfWeek.contains(toCronDayOfWeek(candidate.getDayOfWeek()));
        boolean dayMatches;
        if (dayOfMonthWildcard && dayOfWeekWildcard) {
            dayMatches = true;
        } else if (dayOfMonthWildcard) {
            dayMatches = dayOfWeekMatches;
        } else if (dayOfWeekWildcard) {
            dayMatches = dayOfMonthMatches;
        } else {
            dayMatches = dayOfMonthMatches || dayOfWeekMatches;
        }

        return minutes.contains(candidate.getMinute())
                && hours.contains(candidate.getHour())
                && dayMatches
                && months.contains(candidate.getMonthValue());
    }

    private static int toCronDayOfWeek(DayOfWeek dayOfWeek) {
        return dayOfWeek == DayOfWeek.SUNDAY ? 0 : dayOfWeek.getValue();
    }

    private static Set<Integer> parseField(String field, int min, int max, Set<NameValue> names) {
        Set<Integer> values = new HashSet<>();
        for (String part : field.split(",")) {
            parsePart(part.trim().toUpperCase(Locale.ROOT), min, max, names, values);
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Cron field must not be empty: " + field);
        }
        if (max == 7 && values.contains(7)) {
            values.add(0);
            values.remove(7);
        }
        return Set.copyOf(values);
    }

    private static void parsePart(String part, int min, int max, Set<NameValue> names, Set<Integer> values) {
        if (part.isBlank()) {
            throw new IllegalArgumentException("Cron field contains an empty list item.");
        }
        String base = part;
        int step = 1;
        int slash = part.indexOf('/');
        if (slash >= 0) {
            base = part.substring(0, slash);
            step = parseNumber(part.substring(slash + 1), names);
            if (step <= 0) {
                throw new IllegalArgumentException("Cron step must be greater than zero: " + part);
            }
        }

        int start;
        int end;
        if ("*".equals(base)) {
            start = min;
            end = max;
        } else if (base.contains("-")) {
            String[] range = base.split("-", 2);
            start = parseNumber(range[0], names);
            end = parseNumber(range[1], names);
        } else {
            start = parseNumber(base, names);
            end = start;
        }

        if (start < min || end > max || start > end) {
            throw new IllegalArgumentException("Cron value out of range: " + part);
        }
        for (int value = start; value <= end; value += step) {
            values.add(value);
        }
    }

    private static int parseNumber(String value, Set<NameValue> names) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Cron value must not be empty.");
        }
        if (names != null) {
            for (NameValue name : names) {
                if (name.name().equals(value)) {
                    return name.value();
                }
            }
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid cron value: " + value, e);
        }
    }

    private static Set<NameValue> monthNames() {
        Set<NameValue> names = new HashSet<>();
        for (Month month : Month.values()) {
            names.add(new NameValue(month.name().substring(0, 3), month.getValue()));
        }
        return names;
    }

    private static Set<NameValue> dayNames() {
        return Set.of(
                new NameValue("SUN", 0),
                new NameValue("MON", 1),
                new NameValue("TUE", 2),
                new NameValue("WED", 3),
                new NameValue("THU", 4),
                new NameValue("FRI", 5),
                new NameValue("SAT", 6)
        );
    }

    private record NameValue(String name, int value) {
    }
}
