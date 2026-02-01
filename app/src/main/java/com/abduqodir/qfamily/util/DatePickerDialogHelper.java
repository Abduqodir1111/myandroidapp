package com.abduqodir.qfamily.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;
import androidx.annotation.Nullable;
import com.abduqodir.qfamily.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.time.LocalDate;
import java.time.YearMonth;

public final class DatePickerDialogHelper {
    public interface OnDateSelectedListener {
        void onDateSelected(LocalDate date);
    }

    private DatePickerDialogHelper() {
    }

    public static void show(Context context,
                            @Nullable LocalDate initialDate,
                            @Nullable OnDateSelectedListener listener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_date_picker, null);
        NumberPicker dayPicker = view.findViewById(R.id.pickerDay);
        NumberPicker monthPicker = view.findViewById(R.id.pickerMonth);
        NumberPicker yearPicker = view.findViewById(R.id.pickerYear);

        LocalDate today = LocalDate.now();
        int initialYear = initialDate != null ? initialDate.getYear() : today.getYear();
        int initialMonth = initialDate != null ? initialDate.getMonthValue() : today.getMonthValue();
        int initialDay = initialDate != null ? initialDate.getDayOfMonth() : today.getDayOfMonth();

        int minYear = 1900;
        int maxYear = today.getYear();

        yearPicker.setMinValue(minYear);
        yearPicker.setMaxValue(maxYear);
        yearPicker.setWrapSelectorWheel(false);
        yearPicker.setValue(clamp(initialYear, minYear, maxYear));

        monthPicker.setMinValue(1);
        monthPicker.setMaxValue(12);
        monthPicker.setWrapSelectorWheel(true);
        monthPicker.setValue(initialMonth);

        setupDayPicker(dayPicker, yearPicker.getValue(), monthPicker.getValue(), initialDay);

        NumberPicker.OnValueChangeListener updateListener = (picker, oldVal, newVal) -> {
            int year = yearPicker.getValue();
            int month = monthPicker.getValue();
            int day = dayPicker.getValue();
            setupDayPicker(dayPicker, year, month, day);
        };
        yearPicker.setOnValueChangedListener(updateListener);
        monthPicker.setOnValueChangedListener(updateListener);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.birth_date_hint)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int year = yearPicker.getValue();
                    int month = monthPicker.getValue();
                    int day = dayPicker.getValue();
                    LocalDate selected = LocalDate.of(year, month, day);
                    if (listener != null) {
                        listener.onDateSelected(selected);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void setupDayPicker(NumberPicker dayPicker, int year, int month, int preferredDay) {
        int maxDay = YearMonth.of(year, month).lengthOfMonth();
        dayPicker.setMinValue(1);
        dayPicker.setMaxValue(maxDay);
        dayPicker.setWrapSelectorWheel(true);
        dayPicker.setValue(clamp(preferredDay, 1, maxDay));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

