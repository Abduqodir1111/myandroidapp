package com.example.myappandroid.util;

import android.content.Context;
import com.example.myappandroid.R;
import com.example.myappandroid.data.Person;
import java.time.LocalDate;

public final class PersonFormatter {
    private PersonFormatter() {
    }

    public static String getFullName(Person person) {
        if (person == null) {
            return "";
        }
        String lastName = safe(person.lastName);
        String firstName = safe(person.firstName);
        String middleName = safe(person.middleName);
        String result = (lastName + " " + firstName + " " + middleName).trim();
        return result.replaceAll("\\s+", " ");
    }

    public static String getShortName(Person person) {
        if (person == null) {
            return "";
        }
        String lastName = safe(person.lastName);
        String firstName = safe(person.firstName);
        String middleName = safe(person.middleName);
        String initials = InitialsUtils.buildInitials(null, firstName, middleName);
        if (lastName.isEmpty()) {
            return initials;
        }
        if (initials.isEmpty()) {
            return lastName;
        }
        return lastName + " " + initials;
    }

    public static String buildBirthLine(Context context, Person person) {
        if (context == null || person == null || person.birthDate == null) {
            return context != null ? context.getString(R.string.birth_date_line, context.getString(R.string.no_data)) : "";
        }
        LocalDate date = DateUtils.fromEpochMillis(person.birthDate);
        if (date == null) {
            return context.getString(R.string.birth_date_line, context.getString(R.string.no_data));
        }
        String formatted = DateUtils.formatDate(date);
        Integer age = DateUtils.calculateAge(date);
        if (age == null) {
            return context.getString(R.string.birth_date_line, formatted);
        }
        return context.getString(R.string.birth_date_and_age_line, formatted, age);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
