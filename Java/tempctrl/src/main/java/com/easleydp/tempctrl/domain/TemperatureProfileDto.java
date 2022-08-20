package com.easleydp.tempctrl.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Marries-up with the the contents of profile.json. See also
 * TemperatureProfile, which extends this class.
 */
public class TemperatureProfileDto {
    protected List<PointDto> points = new ArrayList<>();

    public TemperatureProfileDto() {
    }

    public TemperatureProfileDto(List<PointDto> points) {
        setPoints(points);
    }

    public List<PointDto> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public void setPoints(List<PointDto> points) {
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
        return "{" + " points=" + points + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof TemperatureProfileDto)) {
            return false;
        }
        TemperatureProfileDto temperatureProfileDto = (TemperatureProfileDto) o;
        return java.util.Objects.equals(points, temperatureProfileDto.points);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(points);
    }
}
