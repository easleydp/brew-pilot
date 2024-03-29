package com.easleydp.tempctrl.domain;

import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Represents a temperature profile defined by a user.
 * <li>The first point represents the start temperature; must have
 * `millisSinceStart` of 0.
 * <li>Each successive point has a `millisSinceStart` value greater than the
 * previous.
 * <li>The last point represents the final temperature at which the beer should
 * be held indefinitely.
 *
 * This class takes care of interpolating between set points to calculate a
 * target temperature at any point in time.
 */
public class TemperatureProfile extends TemperatureProfileDto {
    private static final Logger logger = LoggerFactory.getLogger(TemperatureProfile.class);

    public TemperatureProfile(TemperatureProfileDto temperatureProfileDto) {
        BeanUtils.copyProperties(temperatureProfileDto, this);
    }

    public TemperatureProfile() {
    }

    /**
     * @param targetTemp
     *            target temperature (degrees x 10)
     */
    public void addPoint(int hoursSinceStart, int targetTemp) {
        PointDto point = new PointDto(hoursSinceStart, targetTemp);

        // If & when needed this method can be enhanced to allow adding points in any
        // order but for now we insist
        // points are added in order.
        if (points.isEmpty()) {
            if (hoursSinceStart != 0)
                throw new IllegalArgumentException("First point must have be at t0");
        } else {
            if (hoursSinceStart <= points.get(points.size() - 1).getHoursSinceStart())
                throw new IllegalArgumentException("Point being added must occur after last point");
        }
        points.add(point);
    }

    /**
     * @return the target temperature (degrees x 10) at any given time since the
     *         profile started.
     *
     *         If the specified millisSinceStart is between two points an
     *         interpolated value is calculated.
     *
     *         If the specified millisSinceStart is before/after the first/last
     *         point the first/last set point temperature is returned.
     */
    public int getTargetTempAt(final long millisSinceStart) {
        if (points.isEmpty())
            throw new IllegalStateException("Profile has no points");

        PointDto firstPoint = points.get(0);
        int p0HoursSinceStart = firstPoint.getHoursSinceStart();
        if (p0HoursSinceStart != 0)
            throw new IllegalStateException("First profile point isn't at t0: " + p0HoursSinceStart);

        if (millisSinceStart < 0)
            return firstPoint.getTargetTemp();

        PointDto prevPoint = null;
        PointDto point = null;
        for (Iterator<PointDto> iter = points.iterator(); iter.hasNext();) {
            prevPoint = point;
            point = iter.next();

            if (point.getMillisSinceStart() == millisSinceStart)
                return point.getTargetTemp();

            if (point.getMillisSinceStart() > millisSinceStart) {
                // Interpolate
                Assert.state(prevPoint != null, "prevPoint should be non-null");
                final int yA = prevPoint.getTargetTemp();
                final int yB = point.getTargetTemp();
                final long xA = prevPoint.getMillisSinceStart();
                final long xB = point.getMillisSinceStart();
                final long x = millisSinceStart;
                // https://en.wikipedia.org/wiki/Interpolation#Linear_interpolation
                return Math.round(yA + (yB - yA) * (x - xA) / (xB - xA));
            }
        }
        return point.getTargetTemp();
    }

    /**
     * Locates the start of the cold crash.
     *
     * @return The point that looks like the start of the cold crash phase, or null
     *         if none.
     *
     *         Specifically, we look for the first point that sits immediately
     *         between a flat section to the left and a downwards ramp to the right
     *         where the gradient is at least 0.5°C/hr and temp drop is at least
     *         5°C.
     */
    @JsonIgnore // In case this DTO subclass is ever serialised
    public PointDto getCrashStartPoint() {
        if (points.size() < 3) {
            return null;
        }
        // Get the points as an array so we can easily look ahead.
        PointDto pts[] = points.toArray(new PointDto[0]);
        int len = pts.length;

        // Look for a point between a flat section and a significant downwards ramp.
        PointDto prevPt = pts[0];
        for (int i = 1; i < len - 1; i++) { // Note: Deliberately ignoring the last point
            PointDto pt = pts[i];
            if (prevPt.getTargetTemp() == pt.getTargetTemp()) {
                PointDto nextPt = pts[i + 1];
                int tempDrop = pt.getTargetTemp() - nextPt.getTargetTemp();
                if (tempDrop >= 50) { // Remember, temperature values are degrees x10
                    int hours = nextPt.getHoursSinceStart() - pt.getHoursSinceStart();
                    int gradient = tempDrop / hours;
                    if (gradient >= 5) { // i.e. must be 0.5°C/hr or steeper
                        return pt;
                    }
                }
            }
            prevPt = pt;
        }
        return null;
    }

    /**
     * Locates the end of the cold crash.
     *
     * @return The point that looks like the end of the cold crash phase, or null if
     *         none.
     *
     *         Specifically, we first check for the presence of a crashStartPoint,
     *         then, if found return the last point in the profile.
     */
    @JsonIgnore // In case this DTO subclass is ever serialised
    public PointDto getCrashEndPoint() {
        return getCrashStartPoint() != null ? points.get(points.size() - 1) : null;
    }
}
