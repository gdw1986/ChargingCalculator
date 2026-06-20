package com.example.chargingcalculator;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.LinkedHashSet;
import java.util.Set;

public class ChargingNotificationListenerService extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;

        String notificationText = extractNotificationText(sbn.getNotification());
        ChargingNotificationParser.Result result =
                ChargingNotificationParser.parse(notificationText, sbn.getPostTime());
        ChargingNotificationStore.save(this, result);
    }

    private String extractNotificationText(Notification notification) {
        if (notification == null || notification.extras == null) return "";

        Bundle extras = notification.extras;
        Set<String> parts = new LinkedHashSet<>();
        add(parts, extras.getCharSequence(Notification.EXTRA_TITLE));
        add(parts, extras.getCharSequence(Notification.EXTRA_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));

        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) add(parts, line);
        }

        StringBuilder combined = new StringBuilder();
        for (String part : parts) {
            if (combined.length() > 0) combined.append('\n');
            combined.append(part);
        }
        return combined.toString();
    }

    private void add(Set<String> parts, CharSequence value) {
        if (value == null) return;
        String text = value.toString().trim();
        if (!TextUtils.isEmpty(text)) parts.add(text);
    }
}
