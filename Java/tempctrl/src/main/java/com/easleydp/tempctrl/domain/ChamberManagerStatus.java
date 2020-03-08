package com.easleydp.tempctrl.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Value object representing the status reported from a chamber manager.
 */
public class ChamberManagerStatus
{
    public ChamberManagerStatus(
            int uptimeMins, int minFreeRam, boolean temperatureSensorsOk, boolean logDataEjected)
    {
        this.uptimeMins = uptimeMins;
        this.minFreeRam = minFreeRam;
        this.temperatureSensorsOk = temperatureSensorsOk;
        this.logDataEjected = logDataEjected ? true : null;
    }

    private final int uptimeMins;
    public final int minFreeRam;
    public final boolean temperatureSensorsOk;
    @JsonInclude(Include.NON_NULL)
    public final Boolean logDataEjected;

    /** @returns string such as "19 hours, 31 minutes" */
    public String getUptime()
    {
        int mins = uptimeMins;
        if (mins < 60)
            return minutesPart(mins);

        int hours = mins / 60;
        mins -= hours * 60;
        if (hours < 24)
            return hoursPart(hours) + ", " + minutesPart(mins);

        int days = hours / 24;
        hours -= days * 24;
        return daysPart(days) + ", " + hoursPart(hours) + ", " + minutesPart(mins);
    }
    private static String daysPart(int days)
    {
        return days + (days == 1 ? " day" : " days");
    }
    private static String hoursPart(int hours)
    {
        return hours + (hours == 1 ? " hour" : " hours");
    }
    private static String minutesPart(int mins)
    {
        return mins + (mins == 1 ? " minute" : " minutes");
    }
}
