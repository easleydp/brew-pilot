package com.easleydp.tempctrl.domain;

import com.easleydp.tempctrl.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Value object representing the status reported from a chamber manager.
 */
@JsonPropertyOrder({ "uptime", "healthMessage", "minFreeRam", "minFreeRamLocation", "badSensorCount", "logDataEjected" })
public class ChamberManagerStatus
{
    public ChamberManagerStatus(
            int uptimeMins, int minFreeRam, int minFreeRamLocation, int badSensorCount, boolean logDataEjected)
    {
        this.uptimeMins = uptimeMins;
        this.minFreeRam = minFreeRam;
        this.minFreeRamLocation = minFreeRamLocation;
        this.badSensorCount = badSensorCount;
        this.logDataEjected = logDataEjected;
    }

    private final int uptimeMins;
    public final int minFreeRam;
    public final int minFreeRamLocation;
    public final int badSensorCount;
    public final boolean logDataEjected;

    /** @returns string such as "19 hours, 31 minutes" */
    public String getUptime()
    {
        return StringUtils.friendlyUptime(uptimeMins);
    }

    public String getHealthMessage()
    {
        if (badSensorCount > 0)
            return "🤕 Bad sensor(s)";
        if (logDataEjected)
            return "🤕 Log data ejected";
        return "🙂 All good";
    }
}
