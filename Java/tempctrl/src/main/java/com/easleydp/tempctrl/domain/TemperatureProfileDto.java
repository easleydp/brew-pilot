package com.easleydp.tempctrl.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Marries-up with the the contents of profile.json.
 * See also TemperatureProfile, which extends this class.
 */
public class TemperatureProfileDto
{
    protected List<PointDto> points = new ArrayList<>();

    public TemperatureProfileDto() {}

    public TemperatureProfileDto(List<PointDto> points) {
        setPoints(points);
	}

	public List<PointDto> getPoints()
    {
        return Collections.unmodifiableList(points);
    }

    public void setPoints(List<PointDto> points)
    {
        if (points == null)
            throw new IllegalArgumentException("points is required");
        if (points.isEmpty())
            throw new IllegalArgumentException("points cannot be empty");
        int lastHoursSinceStart = Integer.MIN_VALUE;
        for (PointDto point : points) {
            if (point.getHoursSinceStart() < lastHoursSinceStart)
                throw new IllegalArgumentException("Each point must have hoursSinceStart > the previous point");
            lastHoursSinceStart = point.getHoursSinceStart();
        }

        this.points = new ArrayList<>(points);
    }

    @Override
    public String toString() {
        return "{" +
            " points=" + getPoints() +
            "}";
    }

    public static class PointDto
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
        public String toString() {
            return "{" +
                " hoursSinceStart=" + hoursSinceStart +
                ", targetTemp=" + targetTemp +
                "}";
        }
    }
}
