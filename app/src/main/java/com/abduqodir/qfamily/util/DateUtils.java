package com.abduqodir.qfamily.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DateUtils {
    private static final String DATE_PATTERN = "dd.MM.yyyy";

    private DateUtils() {
    }

    public static String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.getDefault());
        return date.format(formatter);
    }

    public static LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.getDefault());
        return LocalDate.parse(value.trim(), formatter);
    }

    public static long toEpochMillis(LocalDate date) {
        if (date == null) {
            return 0L;
        }
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static LocalDate fromEpochMillis(Long millis) {
        if (millis == null || millis <= 0) {
            return null;
        }
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static Integer calculateAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        if (birthDate.isAfter(today)) {
            return null;
        }
        int years = Period.between(birthDate, today).getYears();
        if (years < 0 || years > 130) {
            return null;
        }
        return years;
    }
}

