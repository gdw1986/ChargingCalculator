package com.example.chargingcalculator;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.Locale;

final class ChargingNotificationStore {
    static final String ACTION_NOTIFICATION_TIMES_CHANGED =
            "com.example.chargingcalculator.ACTION_NOTIFICATION_TIMES_CHANGED";

    private static final String PREFS_NAME = "ChargingPrefs";
    private static final String KEY_AUTO_START_TIME = "auto_start_time";
    private static final String KEY_AUTO_END_TIME = "auto_end_time";
    private static final String KEY_AUTO_LAST_UPDATED = "auto_last_updated";

    private ChargingNotificationStore() {
    }

    static boolean save(Context context, ChargingNotificationParser.Result result) {
        if (result == null) return false;
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        boolean changed = false;
        if (!TextUtils.isEmpty(result.startTime)) {
            editor.putString(KEY_AUTO_START_TIME, result.startTime);
            changed = true;
        }
        if (!TextUtils.isEmpty(result.endTime)) {
            editor.putString(KEY_AUTO_END_TIME, result.endTime);
            changed = true;
        }
        if (changed) {
            editor.putLong(KEY_AUTO_LAST_UPDATED, System.currentTimeMillis());
            editor.apply();
        }
        return changed;
    }

    static Snapshot load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new Snapshot(
                prefs.getString(KEY_AUTO_START_TIME, ""),
                prefs.getString(KEY_AUTO_END_TIME, ""),
                prefs.getLong(KEY_AUTO_LAST_UPDATED, 0L));
    }

    static boolean isNotificationListenerEnabled(Context context) {
        String enabledListeners = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(enabledListeners)) return false;

        String componentName = new ComponentName(
                context, ChargingNotificationListenerService.class).flattenToString();
        String packageName = context.getPackageName();
        String[] listeners = enabledListeners.split(":");
        for (String listener : listeners) {
            if (componentName.equalsIgnoreCase(listener)
                    || listener.toLowerCase(Locale.ROOT).contains(packageName.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    static final class Snapshot {
        final String startTime;
        final String endTime;
        final long lastUpdatedMillis;

        Snapshot(String startTime, String endTime, long lastUpdatedMillis) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.lastUpdatedMillis = lastUpdatedMillis;
        }
    }
}
