package com.ebook.reader.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.ebook.reader.model.ReadingRecord;

/**
 * 阅读进度持久化
 */
public class ProgressStore {

    private static final String PREFS_NAME = "reading_progress";

    public static void saveReadingRecord(Context context, ReadingRecord record) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = "progress_" + record.bookName;
        String value = record.herf + "|||" + record.scrollY;
        prefs.edit().putString(key, value).apply();
    }

    public static ReadingRecord getReadingRecord(Context context, String bookName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = "progress_" + bookName;
        String value = prefs.getString(key, null);
        if (value == null) return null;

        String[] parts = value.split("\\|\\|\\|");
        if (parts.length != 2) return null;

        try {
            int scrollY = Integer.parseInt(parts[1]);
            return new ReadingRecord(bookName, parts[0], scrollY);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
