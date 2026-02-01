package com.example.myappandroid.util;

import java.time.LocalDate;

public final class ValidationUtils {
    private ValidationUtils() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isValidBirthDate(LocalDate date) {
        if (date == null) {
            return false;
        }
        return DateUtils.calculateAge(date) != null;
    }
}
