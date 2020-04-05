package com.easleydp.tempctrl.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.*;
import java.util.stream.*;

/**
 * Value object representing the status reported from a chamber manager.
 */
public class ChamberManagerStatus
{
    public ChamberManagerStatus(
            int uptimeMins, int minFreeRam, int badSensorCount, boolean logDataEjected)
    {
        this.uptimeMins = uptimeMins;
        this.minFreeRam = minFreeRam;
        this.badSensorCount = badSensorCount;
        this.logDataEjected = logDataEjected ? true : null;
    }

    private final int uptimeMins;
    public final int minFreeRam;
    public final int badSensorCount;
    @JsonInclude(Include.NON_NULL)
    public final Boolean logDataEjected;

    /** @returns string such as "19 hours, 31 minutes" */
    public String getUptime()
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
