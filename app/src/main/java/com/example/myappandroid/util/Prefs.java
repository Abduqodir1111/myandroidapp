package com.example.myappandroid.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String PREFS_NAME = "onboarding_prefs";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";

    private Prefs() {
    }

    public static boolean isOnboardingDone(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public static void setOnboardingDone(Context context, boolean done) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply();
    }
}
