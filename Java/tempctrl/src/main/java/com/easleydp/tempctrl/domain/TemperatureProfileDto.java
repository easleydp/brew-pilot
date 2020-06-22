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
        this.points = points;
	}

	public List<PointDto> getPoints()
    {
        return Collections.unmodifiableList(points);
    }

    public void setPoints(List<PointDto> points)
    {
        this.points = new ArrayList<>(points);
    }

    public static class PointDto
    {
        private int hoursSinceStart;

        /** Degrees x 10, e.g. a value of 175 represents 17.5 degrees. */
        private int targetTemp;

        public PointDto(int hoursSinceStart, int targetTemp)
        {
            this.hoursSinceStart = hoursSinceStart;
            this.targetTemp = targetTemp;
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
            this.hoursSinceStart = hoursSinceStart;
        }
        public int getTargetTemp()
        {
            return targetTemp;
        }
        public void setTargetTemp(int targetTemp)
        {
            this.targetTemp = targetTemp;
        }

        /** Convenience */
        @JsonIgnore
        public long getMillisSinceStart()
        {
            return ((long) hoursSinceStart) * 1000 * 60 * 60;
        }
    }

    @Override
    public String toString() {
        return "{" +
            " points=" + getPoints() +
            "}";
    }
}
