package com.example.chargingcalculator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class OcrTimeParserTest {
    @Test
    public void parseAuto_fillsMissingStartFromTimestampWhenEndWasLabeled() {
        String text = "结束充电提醒\n"
                + "灰驴子在3月17日16:23充电结束，当前电量100%。\n"
                + "2026-03-17 16:23:52\n"
                + "充电提醒\n"
                + "灰驴子在3月17日g:26，当前电量15%。\n"
                + "2026-03-17 09:26:11";

        OcrTimeParser.Result result = OcrTimeParser.parseAuto(text);

        assertEquals("09:26", result.startTime);
        assertEquals("16:23", result.endTime);
    }

    @Test
    public void extractTimes_keepsDatetimeWhenStandaloneTimeExists() {
        String text = "灰驴子在3月17日16:23充电结束，当前电量100%。\n"
                + "2026-03-17 09:26:11";

        List<String> times = OcrTimeParser.extractTimes(text);

        assertTrue(times.contains("16:23"));
        assertTrue(times.contains("09:26:11"));
    }

    @Test
    public void parseAuto_usesTimeOnSameLineAsStartKeyword() {
        String text = "灰驴子在3月17日9：26开始充电，当前电量15%。\n"
                + "灰驴子在3月17日16：23充电结束，当前电量100%。";

        OcrTimeParser.Result result = OcrTimeParser.parseAuto(text);

        assertEquals("09:26", result.startTime);
        assertEquals("16:23", result.endTime);
    }

    @Test
    public void parseAuto_usesMostRecentStartAndEndMessageBlocks() {
        String text = "19:01:34\n"
                + "开始充电提醒\n"
                + "灰驴子在6月20日19:01开始充电，当前电量15%。\n"
                + "2026-06-20 19:01:30\n"
                + "结束充电提醒\n"
                + "灰驴子在6月20日09:01充电结束，当前电量15%。\n"
                + "2026-06-20 19:01:27\n"
                + "开始充电提醒\n"
                + "灰驴子在6月20日18:57开始充电，当前电量15%。\n"
                + "2026-06-20 18:57:01\n"
                + "结束充电提醒\n"
                + "灰驴子在6月20日18:57充电结束，当前电量15%。\n"
                + "2026-06-20 18:57:30";

        OcrTimeParser.Result result = OcrTimeParser.parseAuto(text);

        assertEquals("19:01", result.startTime);
        assertEquals("19:01", result.endTime);
    }

    @Test
    public void parseSingle_usesRequestedMessageTypeInsteadOfFirstTimeOnScreen() {
        String text = "19:01:34\n"
                + "开始充电提醒\n"
                + "灰驴子在6月20日18:57开始充电，当前电量15%。\n"
                + "2026-06-20 18:57:01\n"
                + "结束充电提醒\n"
                + "灰驴子在6月20日09:01充电结束，当前电量15%。\n"
                + "2026-06-20 19:01:27";

        assertEquals("18:57", OcrTimeParser.parseSingle(text, true));
        assertEquals("19:01", OcrTimeParser.parseSingle(text, false));
    }
}
