package com.easleydp.tempctrl.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class PointDto
{
    private int hoursSinceStart;

    /** Degrees x 10, e.g. a value of 175 represents 17.5 degrees. */
    private int targetTemp;

    public PointDto(int hoursSinceStart, int targetTemp)
    {
        setHoursSinceStart(hoursSinceStart);
        setTargetTemp(targetTemp);
    }

    public PointDto()
    {
    }

    public int getHoursSinceStart()
    {
        return hoursSinceStart;
    }
    public void setHoursSinceStart(int hoursSinceStart)
    {
        if (hoursSinceStart < 0)
            throw new IllegalArgumentException("hoursSinceStart must be +ve");
        this.hoursSinceStart = hoursSinceStart;
    }
    public int getTargetTemp()
    {
        return targetTemp;
    }
    public void setTargetTemp(int targetTemp)
    {
        if (-500 < targetTemp  &&  targetTemp < 500)
            this.targetTemp = targetTemp;
        else
            throw new IllegalArgumentException("targetTemp is out of range: " + targetTemp);
    }

    /** Convenience */
    @JsonIgnore
    public long getMillisSinceStart()
    {
        return ((long) hoursSinceStart) * 1000 * 60 * 60;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof PointDto)) {
            return false;
        }
        PointDto pointDto = (PointDto) o;
        return hoursSinceStart == pointDto.hoursSinceStart && targetTemp == pointDto.targetTemp;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(hoursSinceStart, targetTemp);
    }

    @Override
    public String toString() {
        return "{hoursSinceStart=" + hoursSinceStart + ", targetTemp=" + targetTemp + "}";
    }
}
