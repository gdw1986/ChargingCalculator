package com.example.chargingcalculator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OcrTimeParser {
    private static final String OCR_DIGIT = "[0-9Oo〇Il|]";
    private static final String NOT_OCR_DIGIT = "0-9Oo〇Il|";

    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(?<![" + NOT_OCR_DIGIT + "\\-:])(" + OCR_DIGIT + "{1,2})\\s*:\\s*("
                    + OCR_DIGIT + "{2})(?!\\s*:\\s*" + OCR_DIGIT + ")(?![" + NOT_OCR_DIGIT + "])");

    private static final Pattern DATE_TIME_PATTERN = Pattern.compile(
            "(" + OCR_DIGIT + "{4})\\s*[-/.年]\\s*(" + OCR_DIGIT + "{1,2})\\s*[-/.月]\\s*("
                    + OCR_DIGIT + "{1,2})\\s*日?\\s*(" + OCR_DIGIT + "{1,2})\\s*:\\s*("
                    + OCR_DIGIT + "{2})(?:\\s*:\\s*(" + OCR_DIGIT + "{2}))?");

    private OcrTimeParser() {
    }

    static Result parseAuto(String text) {
        String normalizedText = normalizeOcrText(text);
        String[] lines = normalizedText.split("\\r?\\n|\\r");

        Result result = new Result();

        for (String line : lines) {
            Label label = labelForLine(line);
            if (label == Label.NONE) continue;

            Matcher matcher = TIME_PATTERN.matcher(line);
            while (matcher.find()) {
                TimeCandidate candidate = toTimeCandidate(matcher);
                if (candidate != null) assignLabeledTime(result, label, candidate.minuteValue);
            }
        }

        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = DATE_TIME_PATTERN.matcher(lines[i]);
            while (matcher.find()) {
                TimeCandidate candidate = toDateTimeCandidate(matcher);
                if (candidate == null) continue;

                Label label = findNearestLabel(lines, i);
                assignLabeledTime(result, label, candidate.minuteValue);
            }
        }

        fillMissingTimes(result, extractTimeCandidates(normalizedText));
        return result;
    }

    private static void assignLabeledTime(Result result, Label label, String time) {
        if (label == Label.START && result.startTime == null) {
            result.startTime = time;
        } else if (label == Label.END && result.endTime == null) {
            result.endTime = time;
        }
    }

    static List<String> extractTimes(String text) {
        List<String> times = new ArrayList<>();
        for (TimeCandidate candidate : extractTimeCandidates(text)) {
            if (!times.contains(candidate.value)) {
                times.add(candidate.value);
            }
        }
        return times;
    }

    private static void fillMissingTimes(Result result, List<TimeCandidate> candidates) {
        List<TimeCandidate> fallbackCandidates = uniqueFallbackCandidates(candidates);
        if (fallbackCandidates.isEmpty()) return;

        if (result.startTime == null && result.endTime == null) {
            if (fallbackCandidates.size() == 1) {
                result.startTime = fallbackCandidates.get(0).minuteValue;
                result.endTime = fallbackCandidates.get(0).minuteValue;
                return;
            }
            sortForFallback(fallbackCandidates);
            result.startTime = fallbackCandidates.get(0).minuteValue;
            result.endTime = fallbackCandidates.get(fallbackCandidates.size() - 1).minuteValue;
            return;
        }

        sortForFallback(fallbackCandidates);
        if (result.startTime == null) {
            TimeCandidate start = firstDifferentFrom(fallbackCandidates, result.endTime);
            if (start != null) result.startTime = start.minuteValue;
        }
        if (result.endTime == null) {
            TimeCandidate end = lastDifferentFrom(fallbackCandidates, result.startTime);
            if (end != null) result.endTime = end.minuteValue;
        }
    }

    private static List<TimeCandidate> extractTimeCandidates(String text) {
        String normalizedText = normalizeOcrText(text);
        List<TimeCandidate> candidates = new ArrayList<>();

        Matcher timeMatcher = TIME_PATTERN.matcher(normalizedText);
        while (timeMatcher.find()) {
            TimeCandidate candidate = toTimeCandidate(timeMatcher);
            if (candidate != null) candidates.add(candidate);
        }

        Matcher dateTimeMatcher = DATE_TIME_PATTERN.matcher(normalizedText);
        while (dateTimeMatcher.find()) {
            TimeCandidate candidate = toDateTimeCandidate(dateTimeMatcher);
            if (candidate != null) candidates.add(candidate);
        }

        candidates.sort((a, b) -> Integer.compare(a.position, b.position));
        return candidates;
    }

    private static List<TimeCandidate> uniqueFallbackCandidates(List<TimeCandidate> candidates) {
        Map<String, TimeCandidate> byMinute = new LinkedHashMap<>();
        for (TimeCandidate candidate : candidates) {
            TimeCandidate existing = byMinute.get(candidate.minuteValue);
            if (existing == null || shouldReplace(existing, candidate)) {
                byMinute.put(candidate.minuteValue, candidate);
            }
        }
        return new ArrayList<>(byMinute.values());
    }

    private static boolean shouldReplace(TimeCandidate existing, TimeCandidate candidate) {
        if (!existing.hasDate && candidate.hasDate) return true;
        return existing.value.length() == 5 && candidate.value.length() == 8;
    }

    private static void sortForFallback(List<TimeCandidate> candidates) {
        int datedCount = 0;
        for (TimeCandidate candidate : candidates) {
            if (candidate.hasDate) datedCount++;
        }
        if (datedCount >= 2) {
            candidates.sort((a, b) -> {
                if (a.hasDate && b.hasDate) return Long.compare(a.sortKey, b.sortKey);
                if (a.hasDate) return -1;
                if (b.hasDate) return 1;
                return Integer.compare(a.secondsOfDay, b.secondsOfDay);
            });
        } else {
            candidates.sort((a, b) -> Integer.compare(a.secondsOfDay, b.secondsOfDay));
        }
    }

    private static TimeCandidate firstDifferentFrom(List<TimeCandidate> candidates, String time) {
        String minute = toMinuteValue(time);
        for (TimeCandidate candidate : candidates) {
            if (!candidate.minuteValue.equals(minute)) return candidate;
        }
        return null;
    }

    private static TimeCandidate lastDifferentFrom(List<TimeCandidate> candidates, String time) {
        String minute = toMinuteValue(time);
        for (int i = candidates.size() - 1; i >= 0; i--) {
            TimeCandidate candidate = candidates.get(i);
            if (!candidate.minuteValue.equals(minute)) return candidate;
        }
        return null;
    }

    private static Label findNearestLabel(String[] lines, int timestampLineIndex) {
        int start = Math.max(0, timestampLineIndex - 5);
        for (int i = timestampLineIndex; i >= start; i--) {
            Label label = labelForLine(lines[i]);
            if (label != Label.NONE) return label;
        }

        int end = Math.min(lines.length - 1, timestampLineIndex + 3);
        for (int i = timestampLineIndex + 1; i <= end; i++) {
            Label label = labelForLine(lines[i]);
            if (label != Label.NONE) return label;
        }

        return Label.NONE;
    }

    private static Label labelForLine(String line) {
        String compact = line.replaceAll("\\s+", "");
        boolean start = looksLikeStart(compact);
        boolean end = looksLikeEnd(compact);
        if (start && !end) return Label.START;
        if (end && !start) return Label.END;
        return Label.NONE;
    }

    private static boolean looksLikeStart(String line) {
        return line.contains("开始")
                || line.contains("開始")
                || line.contains("起始")
                || line.matches(".*[开幵升井][始姑治台绐].*");
    }

    private static boolean looksLikeEnd(String line) {
        return line.contains("结束")
                || line.contains("結束")
                || line.contains("结東")
                || line.contains("結東")
                || line.contains("停止")
                || line.contains("充满")
                || line.contains("已满");
    }

    private static TimeCandidate toTimeCandidate(Matcher matcher) {
        int hour = parseOcrNumber(matcher.group(1));
        int minute = parseOcrNumber(matcher.group(2));
        if (!isValidTime(hour, minute, 0)) return null;

        String value = formatMinute(hour, minute);
        return new TimeCandidate(value, value, hour * 3600 + minute * 60,
                matcher.start(), false, hour * 3600L + minute * 60L);
    }

    private static TimeCandidate toDateTimeCandidate(Matcher matcher) {
        int year = parseOcrNumber(matcher.group(1));
        int month = parseOcrNumber(matcher.group(2));
        int day = parseOcrNumber(matcher.group(3));
        int hour = parseOcrNumber(matcher.group(4));
        int minute = parseOcrNumber(matcher.group(5));
        int second = matcher.group(6) != null ? parseOcrNumber(matcher.group(6)) : 0;
        if (!isValidDate(year, month, day) || !isValidTime(hour, minute, second)) return null;

        String minuteValue = formatMinute(hour, minute);
        String value = matcher.group(6) != null
                ? String.format(Locale.getDefault(), "%s:%02d", minuteValue, second)
                : minuteValue;
        long sortKey = (((year * 13L + month) * 32L + day) * 86400L) + hour * 3600L + minute * 60L + second;
        return new TimeCandidate(value, minuteValue, hour * 3600 + minute * 60 + second,
                matcher.start(), true, sortKey);
    }

    private static boolean isValidDate(int year, int month, int day) {
        return year >= 2000 && year <= 2099 && month >= 1 && month <= 12 && day >= 1 && day <= 31;
    }

    private static boolean isValidTime(int hour, int minute, int second) {
        return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59 && second >= 0 && second <= 59;
    }

    private static int parseOcrNumber(String raw) {
        String normalized = raw
                .replace('O', '0')
                .replace('o', '0')
                .replace('〇', '0')
                .replace('I', '1')
                .replace('l', '1')
                .replace('|', '1');
        return Integer.parseInt(normalized);
    }

    private static String normalizeOcrText(String text) {
        if (text == null) return "";
        StringBuilder normalized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '０' && c <= '９') {
                normalized.append((char) ('0' + (c - '０')));
            } else if (c == '：' || c == '﹕' || c == '꞉') {
                normalized.append(':');
            } else if (c == '－' || c == '–' || c == '—') {
                normalized.append('-');
            } else {
                normalized.append(c);
            }
        }
        return normalized.toString();
    }

    private static String formatMinute(int hour, int minute) {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }

    private static String toMinuteValue(String time) {
        if (time == null || time.length() < 5) return "";
        return time.substring(0, 5);
    }

    static final class Result {
        String startTime;
        String endTime;
    }

    private enum Label {
        NONE,
        START,
        END
    }

    private static final class TimeCandidate {
        final String value;
        final String minuteValue;
        final int secondsOfDay;
        final int position;
        final boolean hasDate;
        final long sortKey;

        TimeCandidate(String value, String minuteValue, int secondsOfDay, int position, boolean hasDate, long sortKey) {
            this.value = value;
            this.minuteValue = minuteValue;
            this.secondsOfDay = secondsOfDay;
            this.position = position;
            this.hasDate = hasDate;
            this.sortKey = sortKey;
        }
    }
}
