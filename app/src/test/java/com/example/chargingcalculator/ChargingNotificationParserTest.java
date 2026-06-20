package com.example.chargingcalculator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ChargingNotificationParserTest {
    @Test
    public void parse_extractsStartTimeFromChargingNotification() {
        String text = "开始充电提醒\n"
                + "灰驴子在6月20日19:01开始充电，当前电量15%。";

        ChargingNotificationParser.Result result = ChargingNotificationParser.parse(text, 0L);

        assertEquals("19:01", result.startTime);
        assertNull(result.endTime);
    }

    @Test
    public void parse_extractsEndTimeFromChargingNotification() {
        String text = "结束充电提醒\n"
                + "灰驴子在6月20日19:01充电结束，当前电量15%。";

        ChargingNotificationParser.Result result = ChargingNotificationParser.parse(text, 0L);

        assertNull(result.startTime);
        assertEquals("19:01", result.endTime);
    }

    @Test
    public void parse_ignoresUnrelatedNotification() {
        assertNull(ChargingNotificationParser.parse("普通车辆消息", 0L));
        assertNull(ChargingNotificationParser.parse("手机开始充电", 0L));
    }
}
