package com.easleydp.tempctrl.domain;

import java.util.Iterator;

import org.springframework.beans.BeanUtils;
import org.springframework.util.Assert;

/**
 * Represents a temperature profile defined by a user.
 * The first point represents the start temperature; must have `millisSinceStart` of 0.
 * Each successive point has a `millisSinceStart` value greater than the previous.
 * The last point represents the final temperature at which the beer should be held indefinitely.
 * This class takes care of interpolating between set points to calculate a target temperature at any point in time.
 */
public class TemperatureProfile extends TemperatureProfileDto
{
    public TemperatureProfile(TemperatureProfileDto temperatureProfileDto)
    {
        BeanUtils.copyProperties(temperatureProfileDto, this);
    }

    public TemperatureProfile()
    {
    }

    /**
     * @param targetTemp  target temperature (degrees x 10)
     */
    public void addPoint(int hoursSinceStart, int targetTemp)
    {
        PointDto point = new PointDto(hoursSinceStart, targetTemp);

        // If & when needed this method can be enhanced to allow adding points in any order but for now we insist
        // points are added in order.
        if (points.isEmpty())
        {
            if (hoursSinceStart != 0)
                throw new IllegalArgumentException("First point must have be at t0");
        }
        else
        {
            if (hoursSinceStart <= points.get(points.size() - 1).getHoursSinceStart())
                throw new IllegalArgumentException("Point being added must occur after last point");
        }
        points.add(point);
    }

    /**
     * @return the target temperature (degrees x 10) at any given time since the profile started.
     * If the specified millisSinceStart is between two points an interpolated value is calculated.
     * If the specified millisSinceStart is after the last point the last set point temperature is returned.
     */
    public int getTargetTempAt(final int millisSinceStart)
    {
        if (millisSinceStart < 0)
            throw new IllegalArgumentException("millisSinceStart cannot be -ve");

        if (points.isEmpty())
            throw new IllegalStateException("Profile has no points");

        if (points.get(0).getHoursSinceStart() != 0)
            throw new IllegalStateException("First profile point isn't at t0");

        PointDto prevPoint = null;
        PointDto point = null;
        for (Iterator<PointDto> iter = points.iterator(); iter.hasNext(); )
        {
            prevPoint = point;
            point = iter.next();

            if (millisSinceStart == point.getMillisSinceStart())
                return point.getTargetTemp();

            if (point.getMillisSinceStart() > millisSinceStart)
            {
                // Interpolate
                Assert.state(prevPoint != null, "prevPoint should be non-null");
                final int yA = prevPoint.getTargetTemp();
                final int yB = point.getTargetTemp();
                final int xA = prevPoint.getMillisSinceStart();
                final int xB = point.getMillisSinceStart();
                final int x = millisSinceStart;
                // https://en.wikipedia.org/wiki/Interpolation#Linear_interpolation
                final int y = yA + (yB - yA) * (x - xA) / (xB - xA);
                return y;
            }
        }
        return point.getTargetTemp();
    }
}
