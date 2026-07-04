package com.example.chargingcalculator;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class ChargingNotificationListenerService extends NotificationListenerService {
    private static volatile ChargingNotificationListenerService activeService;

    static boolean isConnected() {
        return activeService != null;
    }

    static boolean scanActiveNotificationsNow() {
        ChargingNotificationListenerService service = activeService;
        return service != null && service.scanActiveNotifications();
    }

    static void requestRebind(Context context) {
        NotificationListenerService.requestRebind(new ComponentName(
                context, ChargingNotificationListenerService.class));
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (processNotification(sbn)) {
            notifyStateChanged(true);
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        activeService = this;
        notifyStateChanged(scanActiveNotifications());
    }

    @Override
    public void onListenerDisconnected() {
        if (activeService == this) activeService = null;
        super.onListenerDisconnected();
    }

    @Override
    public void onDestroy() {
        if (activeService == this) activeService = null;
        super.onDestroy();
    }

    private boolean scanActiveNotifications() {
        StatusBarNotification[] activeNotifications;
        try {
            activeNotifications = getActiveNotifications();
        } catch (SecurityException ignored) {
            return false;
        }
        if (activeNotifications == null || activeNotifications.length == 0) return false;

        Arrays.sort(activeNotifications, (a, b) -> Long.compare(a.getPostTime(), b.getPostTime()));
        boolean changed = false;
        for (StatusBarNotification activeNotification : activeNotifications) {
            if (processNotification(activeNotification)) changed = true;
        }
        return changed;
    }

    private boolean processNotification(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return false;

        String notificationText = extractNotificationText(sbn.getNotification());
        ChargingNotificationParser.Result result =
                ChargingNotificationParser.parse(notificationText, sbn.getPostTime());
        return ChargingNotificationStore.save(this, result);
    }

    private void notifyStateChanged(boolean timesChanged) {
        Intent intent = new Intent(ChargingNotificationStore.ACTION_NOTIFICATION_TIMES_CHANGED);
        intent.setPackage(getPackageName());
        intent.putExtra(ChargingNotificationStore.EXTRA_TIMES_CHANGED, timesChanged);
        sendBroadcast(intent);
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
