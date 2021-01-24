package com.easleydp.tempctrl.domain;

import com.easleydp.tempctrl.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Value object representing the status reported from a chamber manager.
 */
@JsonPropertyOrder({ "uptime", "healthMessage", "minFreeRam", "minFreeRamLocation", "badSensorCount", "logDataCannibalised" })
public class ChamberManagerStatus
{
    public ChamberManagerStatus(
            int uptimeMins, int minFreeRam, int minFreeRamLocation, int badSensorCount, boolean logDataCannibalised)
    {
        this.uptimeMins = uptimeMins;
        this.minFreeRam = minFreeRam;
        this.minFreeRamLocation = minFreeRamLocation;
        this.badSensorCount = badSensorCount;
        this.logDataCannibalised = logDataCannibalised;
    }

    private final int uptimeMins;
    public final int minFreeRam;
    public final int minFreeRamLocation;
    public final int badSensorCount;
    public final boolean logDataCannibalised;

    /** @returns string such as "19 hours, 31 minutes" */
    public String getUptime()
    {
        return StringUtils.friendlyUptime(uptimeMins);
    }

    public String getHealthMessage()
    {
        if (badSensorCount > 0)
            return "🥵 Bad sensor(s)";
        if (logDataCannibalised)
            return "😨 Log data cannibalised";
        return "🙂 All good";
    }
}
