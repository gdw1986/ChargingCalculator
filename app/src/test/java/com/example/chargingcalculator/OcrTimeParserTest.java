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
}
