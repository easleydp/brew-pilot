package com.easleydp.tempctrl.domain;

import com.easleydp.tempctrl.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Value object representing the status reported from a chamber manager.
 */
@JsonPropertyOrder({ "uptime", "minFreeRam", "badSensorCount" })
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
        return StringUtils.friendlyUptime(uptimeMins);
    }
}
