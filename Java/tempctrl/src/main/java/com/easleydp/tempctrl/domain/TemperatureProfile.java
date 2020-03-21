package com.easleydp.tempctrl.domain;

import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(TemperatureProfile.class);

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
    public int getTargetTempAt(final long millisSinceStart)
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

            if (point.getMillisSinceStart() == millisSinceStart)
                return point.getTargetTemp();

            if (point.getMillisSinceStart() > millisSinceStart)
            {
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
}
