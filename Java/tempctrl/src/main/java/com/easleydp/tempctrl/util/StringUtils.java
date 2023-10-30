package com.easleydp.tempctrl.util;

import java.util.*;
import java.util.stream.*;

public class StringUtils {
    /**
     * Returns true if the supplied string is 'null or empty', where empty is
     * defined as being zero length after being trimmed.
     */
    public static boolean nullOrEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    public static String substringBetween(String str, String before, String after) {
        int i = str.indexOf(before);
        int j = str.indexOf(after, i + before.length());
        if (i == -1)
            throw new RuntimeException("String " + str + " did not contain " + before);
        if (j == -1)
            throw new RuntimeException("String " + str + " did not contain " + after);
        return str.substring(i + before.length(), j);
    }

    public static String substringAfter(String str, String before) {
        int i = str.indexOf(before);
        if (i == -1)
            throw new RuntimeException("String " + str + " did not contain " + before);
        return str.substring(i + before.length());
    }

    /**
     * @returns string in Linux's `uptime -p` format, e.g. "up 19 hours, 31 minutes"
     */
    public static String prettyFormatUptime(int uptimeMins) {
        int mins = uptimeMins;
        if (mins < 60)
            return joinUptimeParts(mins == 0 ? "0 minutes" : minutesPart(mins));

        int hours = mins / 60;
        mins -= hours * 60;
        if (hours < 24)
            return joinUptimeParts(hoursPart(hours), minutesPart(mins));

        int days = hours / 24;
        hours -= days * 24;
        if (days < 7)
            return joinUptimeParts(daysPart(days), hoursPart(hours), minutesPart(mins));

        int weeks = days / 7;
        days -= weeks * 7;
        return joinUptimeParts(weeksPart(weeks), daysPart(days), hoursPart(hours), minutesPart(mins));
    }

    /**
     * Reduces precision of pretty formatted uptime (as returned from Linux's
     * `uptime -p`) and drops the "up " prefix.
     * E.g. "up 4 days, 21 hours, 45 minutes" => "4 days, 21 hours"
     */
    public static String humaniseUptime(String uptime) {
        final String prefix = "up ";
        if (uptime.indexOf(prefix) != 0) {
            throw new IllegalArgumentException("Missing \"up \" prefix: " + uptime);
        }
        uptime = uptime.substring(prefix.length());

        int i = uptime.indexOf("week");
        if (i != -1) {
            // If weeks part is followed by a days part, keep the days part and drop
            // anything after that.
            // Otherwise, drop anything after the weeks part.
            // e.g. "4 weeks, 6 days, 19 hours, 16 minutes" => "4 weeks, 6 days"
            i = uptime.indexOf("day", i);
            if (i != -1) {
                i = uptime.indexOf(",", i);
                if (i != -1) {
                    uptime = uptime.substring(0, i);
                }
            } else {
                i = uptime.indexOf(",");
                if (i != -1) {
                    uptime = uptime.substring(0, i);
                }
            }
        } else {
            i = uptime.indexOf("day");
            if (i != -1) {
                // If days part is followed by an hours part, keep the hours part and drop
                // anything after that.
                // Otherwise, drop anything after the days part.
                // e.g. "6 days, 19 hours, 16 minutes" => "6 days, 19 hours"
                i = uptime.indexOf("hour", i);
                if (i != -1) {
                    i = uptime.indexOf(",", i);
                    if (i != -1) {
                        uptime = uptime.substring(0, i);
                    }
                } else {
                    i = uptime.indexOf(",");
                    if (i != -1) {
                        uptime = uptime.substring(0, i);
                    }
                }
            }
        }
        return uptime;
    }

    private static String weeksPart(int weeks) {
        return nPart(weeks, "week");
    }

    private static String daysPart(int days) {
        return nPart(days, "day");
    }

    private static String hoursPart(int hours) {
        return nPart(hours, "hour");
    }

    private static String minutesPart(int mins) {
        return nPart(mins, "minute");
    }

    private static String nPart(int n, String name) {
        if (n == 0)
            return null;
        return n + " " + (n == 1 ? name : name + 's');
    }

    private static String joinUptimeParts(String... parts) {
        return "up " + Arrays.stream(parts)
                .filter(p -> p != null)
                .collect(Collectors.joining(", "));
    }
}
