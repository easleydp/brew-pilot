package com.easleydp.tempctrl.dto;

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
        validatePoints(points);
        this.points = new ArrayList<>(points);
    }

    public void validatePoints(List<PointDto> points) {
        if (points == null)
            throw new IllegalArgumentException("points is required");
        if (points.isEmpty())
            throw new IllegalArgumentException("points cannot be empty");
        int lastHoursSinceStart = Integer.MIN_VALUE;
        for (PointDto point : points) {
            if (point == null)
                throw new IllegalArgumentException("point cannot be null");
            // Note: It IS legal to have a step change (i.e. two adjacent points with the
            // same time). For example, a user might want to configure a snap cold crash
            // (though, in practise, `TemperatureProfile.getTargetTempAt` will apply
            // smoothing either side).
            if (point.getHoursSinceStart() < lastHoursSinceStart)
                throw new IllegalArgumentException("Each point must have hoursSinceStart >= the previous point");
            lastHoursSinceStart = point.getHoursSinceStart();
        }
        if (points.get(0).getHoursSinceStart() != 0)
            throw new IllegalArgumentException(
                    "First profile point isn't at t0: " + points.get(0).getHoursSinceStart());
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
