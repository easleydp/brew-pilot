package com.easleydp.tempctrl.domain;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.easleydp.tempctrl.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Value object representing the status reported from a chamber manager.
 */
@JsonPropertyOrder({
    "uptime", "healthMessage", "projectBoxTemperature", "minFreeRam", "minFreeRamLocation", "badSensorCount", "logBufferCannibalised"
})
public class ChamberManagerStatus
{
    public ChamberManagerStatus(
            int uptimeMins, int projectBoxTemperature, int minFreeRam, int minFreeRamLocation, int badSensorCount, boolean logBufferCannibalised)
    {
        this.uptimeMins = uptimeMins;
        this.projectBoxTemperature = projectBoxTemperature;
        this.minFreeRam = minFreeRam;
        this.minFreeRamLocation = minFreeRamLocation;
        this.badSensorCount = badSensorCount;
        this.logBufferCannibalised = logBufferCannibalised;
    }

    private final int uptimeMins;
    private final int projectBoxTemperature;
    public final int minFreeRam;
    public final int minFreeRamLocation;
    public final int badSensorCount;
    public final boolean logBufferCannibalised;

    /** @returns string such as "19 hours, 31 minutes" */
    public String getUptime()
    {
        return StringUtils.friendlyUptime(uptimeMins);
    }

    public String getHealthMessage()
    {
        if (badSensorCount > 0)
            return "🥵 Bad sensor(s)";
        if (logBufferCannibalised)
            return "😨 Log buffer cannibalised";
        return "🙂 All good";
    }

    public BigDecimal getProjectBoxTemperature()
    {
        return BigDecimal.valueOf(projectBoxTemperature).scaleByPowerOfTen(-1);
    }
}
