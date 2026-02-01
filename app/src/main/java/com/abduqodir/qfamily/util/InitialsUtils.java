package com.abduqodir.qfamily.util;

public final class InitialsUtils {
    private InitialsUtils() {
    }

    public static String buildInitials(String lastName, String firstName, String middleName) {
        StringBuilder builder = new StringBuilder();
        appendInitial(builder, lastName);
        appendInitial(builder, firstName);
        if (builder.length() < 2) {
            appendInitial(builder, middleName);
        }
        return builder.toString().toUpperCase();
    }

    private static void appendInitial(StringBuilder builder, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        builder.append(trimmed.charAt(0));
    }
}

