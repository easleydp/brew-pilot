package com.easleydp.tempctrl.util;

import java.util.*;
import java.util.stream.*;

public class StringUtils
{
    public static String substringBetween(String str, String before, String after)
    {
        int i = str.indexOf(before);
        int j = str.indexOf(after, i + before.length());
        if (i == -1)
            throw new RuntimeException("String " + str + " did not contain " + before);
        if (j == -1)
            throw new RuntimeException("String " + str + " did not contain " + after);
        return str.substring(i + before.length(), j);
    }

    public static String substringAfter(String str, String before)
    {
        int i = str.indexOf(before);
        if (i == -1)
            throw new RuntimeException("String " + str + " did not contain " + before);
        return str.substring(i + before.length());
    }

    /** @returns string such as "19 hours, 31 minutes" */
    public static String friendlyUptime(int uptimeMins)
    {
        int mins = uptimeMins;
        if (mins < 60)
            return mins == 0 ? "0 minutes" : minutesPart(mins);

        int hours = mins / 60;
        mins -= hours * 60;
        if (hours < 24)
            return joinTimeParts(hoursPart(hours), minutesPart(mins));


        int days = hours / 24;
        hours -= days * 24;
        if (days < 7)
            return joinTimeParts(daysPart(days), hoursPart(hours), minutesPart(mins));

        int weeks = days / 7;
        days -= weeks * 7;
        return joinTimeParts(weeksPart(days), daysPart(days), hoursPart(hours), minutesPart(mins));
    }
    private static String weeksPart(int weeks)
    {
        return nPart(weeks, "week");
    }
    private static String daysPart(int days)
    {
        return nPart(days, "day");
    }
    private static String hoursPart(int hours)
    {
        return nPart(hours, "hour");
    }
    private static String minutesPart(int mins)
    {
        return nPart(mins, "minute");
    }
    private static String nPart(int n, String name)
    {
        if (n == 0)
            return null;
        return n + " " + (n == 1 ? name : name + 's');
    }
    private static String joinTimeParts(String... parts)
    {
        return Arrays.stream(parts)
            .filter(p -> p != null)
            .collect(Collectors.joining(", "));
    }
}
