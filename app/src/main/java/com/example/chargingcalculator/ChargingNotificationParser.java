package com.example.chargingcalculator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class ChargingNotificationParser {
    private ChargingNotificationParser() {
    }

    static Result parse(String text, long fallbackTimeMillis) {
        if (text == null || text.trim().isEmpty()) return null;

        boolean looksLikeStart = looksLikeStart(text);
        boolean looksLikeEnd = looksLikeEnd(text);
        if (!looksLikeStart && !looksLikeEnd) return null;

        Result result = new Result();
        if (looksLikeStart && looksLikeEnd) {
            OcrTimeParser.Result parsed = OcrTimeParser.parseAuto(text);
            result.startTime = parsed.startTime;
            result.endTime = parsed.endTime;
        } else if (looksLikeStart) {
            result.startTime = OcrTimeParser.parseSingle(text, true);
        } else {
            result.endTime = OcrTimeParser.parseSingle(text, false);
        }

        String fallbackTime = formatTime(fallbackTimeMillis);
        if (looksLikeStart && result.startTime == null) result.startTime = fallbackTime;
        if (looksLikeEnd && result.endTime == null) result.endTime = fallbackTime;

        return result.startTime == null && result.endTime == null ? null : result;
    }

    private static boolean looksLikeStart(String text) {
        return text.contains("开始充电提醒")
                || text.contains("開始充電提醒")
                || (text.contains("当前电量") && (text.contains("开始充电") || text.contains("開始充電")));
    }

    private static boolean looksLikeEnd(String text) {
        return text.contains("结束充电提醒")
                || text.contains("結束充電提醒")
                || (text.contains("当前电量")
                && (text.contains("结束充电") || text.contains("充电结束")
                || text.contains("結束充電") || text.contains("充電結束")));
    }

    private static String formatTime(long timeMillis) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timeMillis));
    }

    static final class Result {
        String startTime;
        String endTime;
    }
}
