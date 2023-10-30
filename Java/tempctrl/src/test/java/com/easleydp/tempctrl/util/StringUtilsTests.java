package com.easleydp.tempctrl.util;

import static com.easleydp.tempctrl.util.StringUtils.humaniseUptime;
import static com.easleydp.tempctrl.util.StringUtils.prettyFormatUptime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class StringUtilsTests {
    @Test
    public void testPrettyFormatUptime() {
        final int hour = 60;
        final int day = 24 * hour;
        final int week = 7 * day;

        assertEquals("up 0 minutes", prettyFormatUptime(0));
        assertEquals("up 1 minute", prettyFormatUptime(1));
        assertEquals("up 10 minutes", prettyFormatUptime(10));

        assertEquals("up 1 hour", prettyFormatUptime(hour));
        assertEquals("up 1 hour, 1 minute", prettyFormatUptime(hour + 1));

        assertEquals("up 1 day", prettyFormatUptime(day));
        assertEquals("up 1 day, 1 hour, 1 minute", prettyFormatUptime(day + hour + 1));
        assertEquals("up 1 day, 1 minute", prettyFormatUptime(day + 1));

        assertEquals("up 6 weeks", prettyFormatUptime(6 * week));
        assertEquals("up 2 weeks, 3 days, 2 hours, 5 minutes", prettyFormatUptime(2 * week + 3 * day + 2 * hour + 5));
        assertEquals("up 2 weeks, 2 hours, 5 minutes", prettyFormatUptime(2 * week + 2 * hour + 5));
        assertEquals("up 2 weeks, 5 minutes", prettyFormatUptime(2 * week + 5));
    }

    @Test
    public void testHumaniseUptime() {
        assertEquals("0 minutes", humaniseUptime("up 0 minutes"));
        assertEquals("1 hour", humaniseUptime("up 1 hour"));

        assertEquals("1 day, 1 hour", humaniseUptime("up 1 day, 1 hour, 1 minute"));
        assertEquals("1 day", humaniseUptime("up 1 day, 1 minute"));

        assertEquals("2 weeks, 3 days", humaniseUptime("up 2 weeks, 3 days, 2 hours, 5 minutes"));
        assertEquals("2 weeks", humaniseUptime("up 2 weeks, 2 hours, 5 minutes"));
        assertEquals("2 weeks", humaniseUptime("up 2 weeks, 5 minutes"));
    }
}
