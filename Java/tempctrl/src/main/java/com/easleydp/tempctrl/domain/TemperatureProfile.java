package com.easleydp.tempctrl.domain;

import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.util.Assert;

import com.easleydp.tempctrl.dto.PointDto;
import com.easleydp.tempctrl.dto.TemperatureProfileDto;
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
 * target temperature at any point in time. Stored points remain unchanged;
 * target temperatures cut corners around each waypoint.
 */
public class TemperatureProfile extends TemperatureProfileDto {
    private static final Logger logger = LoggerFactory.getLogger(TemperatureProfile.class);
    private static final long MILLIS_PER_HOUR = 1000L * 60 * 60;
    private static final int DEFAULT_TRANSITION_HOURS = 3;
    private final long transitionHalfWidthMillis;

    public TemperatureProfile(TemperatureProfileDto temperatureProfileDto) {
        this(temperatureProfileDto, DEFAULT_TRANSITION_HOURS);
    }

    public TemperatureProfile(TemperatureProfileDto temperatureProfileDto, int transitionHours) {
        if (transitionHours < 0)
            throw new IllegalArgumentException("transitionHours must not be negative");
        BeanUtils.copyProperties(temperatureProfileDto, this);
        this.transitionHalfWidthMillis = transitionHours * MILLIS_PER_HOUR;
    }

    public TemperatureProfile() {
        this.transitionHalfWidthMillis = DEFAULT_TRANSITION_HOURS * MILLIS_PER_HOUR;
    }

    /**
     * @param targetTemp
     *                   target temperature (degrees x 10)
     */
    public void addPoint(int hoursSinceStart, int targetTemp) {
        PointDto point = new PointDto(hoursSinceStart, targetTemp);

        // If & when needed this method can be enhanced to allow adding points in any
        // order but for now we insist
        // points are added in order.
        if (points.isEmpty()) {
            if (hoursSinceStart != 0)
                throw new IllegalArgumentException("First point must have be at t0");
        } else if (hoursSinceStart < points.get(points.size() - 1).getHoursSinceStart()) {
            throw new IllegalArgumentException("Point being added must not precede the last point");
        }
        points.add(point);
    }

    /**
     * @return the target temperature (degrees x 10) at any given time since the
     *         profile started.
     *
     *         Evaluation order matters: a coincident-time step is checked first
     *         and smoothed across a window centred on the step. Otherwise, a
     *         waypoint corner is cut when the time falls within its transition
     *         window. At all other times, the original profile is linearly
     *         interpolated (or held beyond its final point).
     *
     *         If the specified `millisSinceStart` is between two points an
     *         interpolated value is calculated. In addition, if `millisSinceStart`
     *         is within `transitionHours` of either point then a corner-cutting
     *         function is applied to smooth the transition from one segment of the
     *         profile to the next. (This is analogous to the way an aircraft
     *         follows a flight plan's waypoints in the horizontal domain.)
     *
     *         If the specified millisSinceStart is before the first point then the
     *         first set point temperature is returned.
     *         If the specified millisSinceStart is after the last point then the
     *         last set point temperature is returned UNLESS the time since the last
     *         point is less than `transitionHours` in which case the usual
     *         corner-cutting function is applied (i.e. we cut into the 'held'
     *         temperature).
     *
     *         Consistent with the smoothing policy, if `millisSinceStart` coincides
     *         precisely with a profile step change (i.e. two adjacent points with
     *         the same time) then the the mid-way set temperature of the two
     *         coincident points is returned (visualise an 'S' curve for an upward
     *         step change).
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

        // Check coincident-time points first: these represent instantaneous steps
        // and use a smooth S-curve rather than waypoint corner cutting.
        for (int i = 1; i < points.size(); i++) {
            PointDto beforeStep = points.get(i - 1);
            PointDto afterStep = points.get(i);
            if (beforeStep.getMillisSinceStart() == afterStep.getMillisSinceStart()
                    && beforeStep.getTargetTemp() != afterStep.getTargetTemp()) {
                long transitionHalfWidthMillis = getStepTransitionHalfWidthMillis(i);
                long stepMillis = afterStep.getMillisSinceStart();
                if (transitionHalfWidthMillis > 0
                        && millisSinceStart >= stepMillis - transitionHalfWidthMillis
                        && millisSinceStart <= stepMillis + transitionHalfWidthMillis) {
                    return getStepChangeTargetTempAt(beforeStep, afterStep, stepMillis, millisSinceStart,
                            transitionHalfWidthMillis);
                }
            }
        }

        // Otherwise, look for a waypoint whose corner-cut window contains this time.
        for (int i = 1; i < points.size(); i++) {
            PointDto point = points.get(i);
            long halfWidthMillis = getTransitionHalfWidthMillis(i);
            long pointMillis = point.getMillisSinceStart();
            if (halfWidthMillis > 0 && millisSinceStart >= pointMillis - halfWidthMillis
                    && millisSinceStart <= pointMillis + halfWidthMillis) {
                return getCornerCutTargetTempAt(i, millisSinceStart, halfWidthMillis);
            }
        }

        return (int) Math.round(getLinearTargetTempAt(millisSinceStart));
    }

    /**
     * Returns the step's configured half-window, capped to half the time gap to
     * the nearest distinct-time point on either side. This keeps the smoothed
     * step within its surrounding profile segments; a zero-width result leaves
     * the step unsmoothed.
     */
    private long getStepTransitionHalfWidthMillis(int stepIndex) {
        long halfWidth = transitionHalfWidthMillis;
        long stepMillis = points.get(stepIndex).getMillisSinceStart();

        // Find the closest earlier point at a distinct time to cap the left side.
        for (int i = stepIndex - 1; i >= 0; i--) {
            long pointMillis = points.get(i).getMillisSinceStart();
            if (pointMillis < stepMillis) {
                halfWidth = Math.min(halfWidth, (stepMillis - pointMillis) / 2);
                break;
            }
        }
        // Find the closest later point at a distinct time to cap the right side.
        for (int i = stepIndex + 1; i < points.size(); i++) {
            long pointMillis = points.get(i).getMillisSinceStart();
            if (pointMillis > stepMillis) {
                halfWidth = Math.min(halfWidth, (pointMillis - stepMillis) / 2);
                break;
            }
        }
        return halfWidth;
    }

    /**
     * Smooths an instantaneous step with a cubic smoothstep. The transition
     * begins and ends at the configured window boundaries with zero slope, and
     * reaches the midpoint temperature at the step time.
     */
    private int getStepChangeTargetTempAt(PointDto beforeStep, PointDto afterStep, long stepMillis,
            long millisSinceStart, long halfWidthMillis) {
        double progress = (double) (millisSinceStart - (stepMillis - halfWidthMillis))
                / (2 * halfWidthMillis);
        double easedProgress = progress * progress * (3 - 2 * progress);
        double targetTemp = beforeStep.getTargetTemp()
                + (afterStep.getTargetTemp() - beforeStep.getTargetTemp()) * easedProgress;
        return (int) Math.round(targetTemp);
    }

    private long getTransitionHalfWidthMillis(int pointIndex) {
        if (transitionHalfWidthMillis == 0)
            return 0;
        long previousGap = points.get(pointIndex).getMillisSinceStart()
                - points.get(pointIndex - 1).getMillisSinceStart();
        long halfWidth = Math.min(transitionHalfWidthMillis, previousGap / 2);
        if (pointIndex < points.size() - 1) {
            long nextGap = points.get(pointIndex + 1).getMillisSinceStart()
                    - points.get(pointIndex).getMillisSinceStart();
            halfWidth = Math.min(halfWidth, nextGap / 2);
        }
        return halfWidth;
    }

    /**
     * Cuts the waypoint corner with a quadratic Bezier fillet. The waypoint is
     * the Bezier control point, so the curve approaches and leaves it tangentially
     * without passing through it. Outside the fillet, the original straight
     * profile segments are followed exactly.
     */
    private int getCornerCutTargetTempAt(int pointIndex, long millisSinceStart, long halfWidthMillis) {
        PointDto point = points.get(pointIndex);
        long pointMillis = point.getMillisSinceStart();
        long startMillis = pointMillis - halfWidthMillis;
        long endMillis = pointMillis + halfWidthMillis;
        double startTemp = getLinearTargetTempAt(startMillis);
        double endTemp = pointIndex == points.size() - 1 ? point.getTargetTemp()
                : getLinearTargetTempAt(endMillis);

        // The fillet endpoints are equally spaced from the waypoint in time, so
        // the Bezier parameter advances linearly with millisSinceStart.
        double progress = (double) (millisSinceStart - startMillis) / (2 * halfWidthMillis);
        double remaining = 1 - progress;
        double targetTemp = remaining * remaining * startTemp
                + 2 * remaining * progress * point.getTargetTemp()
                + progress * progress * endTemp;
        return (int) Math.round(targetTemp);
    }

    private double getLinearTargetTempAt(long millisSinceStart) {
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
                return yA + (double) (yB - yA) * (x - xA) / (xB - xA);
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
                    int gradient = hours == 0 ? Integer.MAX_VALUE : tempDrop / hours;
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
