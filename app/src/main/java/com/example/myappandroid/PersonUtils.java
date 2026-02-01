package com.example.myappandroid;

import android.content.Context;
import com.example.myappandroid.data.Person;
import java.util.Calendar;

public final class PersonUtils {
    private PersonUtils() {
    }

    public static String getDisplayName(Person person) {
        if (person == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (person.lastName != null && !person.lastName.trim().isEmpty()) {
            builder.append(person.lastName.trim()).append(" ");
        }
        if (person.firstName != null && !person.firstName.trim().isEmpty()) {
            builder.append(person.firstName.trim()).append(" ");
        }
        if (person.middleName != null && !person.middleName.trim().isEmpty()) {
            builder.append(person.middleName.trim());
        }
        String name = builder.toString().trim();
        if (name.isEmpty() && person.firstName != null) {
            return person.firstName.trim();
        }
        return name;
    }

    public static Integer calculateAge(Integer birthYear) {
        if (birthYear == null) {
            return null;
        }
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        int age = currentYear - birthYear;
        if (age < 0 || age > 130) {
            return null;
        }
        return age;
    }

    public static String buildMetaLine(Context context, Person person) {
        if (context == null || person == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (person.origin != null && !person.origin.trim().isEmpty()) {
            builder.append(person.origin.trim());
        }
        Integer age = calculateAge(person.birthYear);
        if (age != null) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(context.getString(R.string.age_inline, age));
        }
        return builder.toString().trim();
    }

    public static String buildBirthLine(Context context, Person person) {
        if (context == null || person == null) {
            return "";
        }
        if (person.birthYear == null) {
            return context.getString(R.string.birth_year_line, context.getString(R.string.no_data));
        }
        Integer age = calculateAge(person.birthYear);
        if (age != null) {
            return context.getString(R.string.birth_year_and_age_line, person.birthYear, age);
        }
        return context.getString(R.string.birth_year_line, String.valueOf(person.birthYear));
    }
}
