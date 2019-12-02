package com.easleydp.tempctrl.domain;

import java.util.List;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.util.Assert;

/**
 * Removes small fluctuations in a data series
 */
class Smoother
{
    private int threshold;

    /**
     * @param threshold - any fluctuations <= to this value will be smoothed out,
     *                    hence must be > 0 (or no point calling this routine).
     */
    Smoother(int threshold)
    {
        // Doesn't make sense to specify a threshold of 0 as that equates to no smoothing
        Assert.isTrue(threshold > 0, "threshold must be greater than zero");

        this.threshold = threshold;
    }

    /**
     * Smooths a specified 'column' in the supplied array of `records` so as to remove
     * insignificant fluctuations in the property values.
     *
     * The column property is assumed to be of type `int`. In this app it's typically a
     * temperature, where the values are degrees x 10.
     *
     * E.g. for a noise threshold tT (e.g. 0.2°C), if a temperature column has a contiguous range
     * of values that return to (or cross) the starting value (tS) having not deviated by more
     * than tT then all the values up to but not including the crossover point are set to tS.
     * Note, however, local extreme values (peaks and troughs) that exceed the threshold are not
     * smoothed out.
     *
     * @param records - the records, one column of which needs to be smoothed
     * @param intAccessor - an IntPropertyAccessor impl to get & set the column of interest.
     *
     * TODO: The algorithm should preserve local tMin & tMax, not just overall. E.g. before
     * removing a fluctuation, check whether it represents a min or max in the surrounding
     * N points.
     */
    void smoothOutSmallFluctuations(List<Object> records, final IntPropertyAccessor intAccessor)
    {
        if (records == null  ||  records.size() == 0)
            return;

        // Extract the column as a nice simple array. If we remove any noise we'll write it back into the records.
        int[] values = new int[records.size()];
        int i = 0;
        for (Object rec : records)
            values[i++] = intAccessor.getValue(rec);

        boolean noiseWasRemoved = smoothOutSmallFluctuations(values);
        if (noiseWasRemoved)
        {
            i = 0;
            for (Object record : records)
                intAccessor.setValue(record, values[i++]);
        }
    }
    static interface IntPropertyAccessor
    {
        int getValue(Object record);
        void setValue(Object record, int value);
    }

    /**
     * Or just use reflection? Seems to be about x10 slower than using the IntPropertyAccessor
     * version.
     */
    public void smoothOutSmallFluctuations(List records, String propertyName)
    {
        if (records == null  ||  records.size() == 0)
            return;

        // Extract the column as a nice simple array. If we remove any noise we'll write it back into the records.
        int[] values = new int[records.size()];
        int i = 0;
        for (Object rec : records)
        {
            BeanWrapper wrapper = new BeanWrapperImpl(rec);
            values[i++] =  (int) wrapper.getPropertyValue(propertyName);
        }

        boolean noiseWasRemoved = smoothOutSmallFluctuations(values);
        if (noiseWasRemoved)
        {
            i = 0;
            for (Object rec : records)
            {
                BeanWrapper wrapper = new BeanWrapperImpl(rec);
                wrapper.setPropertyValue(propertyName, values[i++]);
            }
        }
    }

    /**
     * This overload works on a simple array of ints. Non-private because it's also handy to unit
     * test at this level (and just have one or two tests to prove the primary method works too).
     * @returns true if any noise was removed.
     */
    boolean smoothOutSmallFluctuations(int[] values)
    {
        Assert.isTrue(values != null  &&  values.length > 0, "Non-empty values array should be supplied");

        final int len = values.length;

        // Optimisation. 3 values or fewer can't possibly need smoothing, e.g. [1, 2, 1],
        // whereas 4 can, e.g. (w/ threshold=1) [1, 3, 2, 3] => [1, 3, 3, 3]
        if (len < 4)
            return false;

//        int minValue = Integer.MAX_VALUE;
//        int maxValue = Integer.MIN_VALUE;
//        for (int value : values)
//        {
//            if (value < minValue)
//                minValue = value;
//            if (value > maxValue)
//                maxValue = value;
//        }

        boolean noiseWasRemoved = false;
        boolean noiseWasRemovedThisPass;
        do {  // Keep scanning the whole array until nothing more needs doing
            noiseWasRemovedThisPass = false;
            boolean firstPeak = true;
            int i = -1;
            while (++i < len)  // Scan forwards through the array (note: i may also be incremented within the loop)
            {
                final int value = values[i];
//                final int valuePlusThreshold = value + threshold;
//                final int valueMinusThreshold = value - threshold;
                // While flat, skip fwd
                while (i + 1 < len  &&  values[i + 1] == value)
                    i++;
                int j = i + 1;
                // So now i refers to the first point OR the last point of a flat section at the
                // start, and j refers to the next point (the first point with a different value),
                // if there is one.

                final boolean incliningUpwards = j < len  &&  value < values[j];
                int iPeak = j;  // Record where we meet the peak (or trough) of the ripple
                int peakVal = j < len ? values[j] : 0;
                int k = j - 1;
                while (++k < len)  // Scan forwards
                {
                    int currVal = values[k];
//                    // Threshold exceeded?
//                    if (currVal < valueMinusThreshold  ||  currVal > valuePlusThreshold)
//                        break;
                    // Track highest/lowest point of ripple so far
                    if (incliningUpwards && peakVal < currVal  ||  !incliningUpwards && peakVal > currVal)
                    {
                        peakVal = currVal;
                        iPeak = k;
                    }
//                    // Hit tMin or tMax?
//                    if (!incliningUpwards && currVal == minValue  ||  incliningUpwards && currVal == maxValue)
//                        break;
                    // Hit or crossed-over the start value?
                    if (incliningUpwards && currVal <= value  ||  !incliningUpwards && currVal >= value)
                    {
                        // Avoid smoothing out significant peak or trough
                        if (incliningUpwards && isSignificantPeak(iPeak, values)  ||  !incliningUpwards && isSignificantTrough(iPeak, values))
                            break;

                        // Since sets of records may subsequently be concatenated together (and
                        // the result smoothed again), avoid smoothing out this insignificant
                        // peak if first or last peak.
                        if (firstPeak)
                        {
                            firstPeak = false;
                            break;
                        }
                        firstPeak = false;
                        // Last peak?
                        if (incliningUpwards && findPeakValue(k, values, true) == null  ||  !incliningUpwards && findTroughValue(k, values, true) == null)
                            break;

                        // Set all points up to but not including the current point to the start value
                        while (j < k)
                            values[j++] = value;
                        noiseWasRemovedThisPass = true;
                        noiseWasRemoved = true;
                        break;
                    }
                }
            }
        } while (noiseWasRemovedThisPass);

        return noiseWasRemoved;
    }

    /**
     * A peak is a value where there are no higher points to the left or right (before the curve
     * begins inclining upwards again). It is significant when the signal difference between the
     * peak and the highest neighbouring trough is > the threshold.
     */
    boolean isSignificantPeak(int k, int[] values)
    {
        Integer leftTroughValue = findTroughValue(k, values, false);
        Integer rightTroughValue = findTroughValue(k, values, true);
        // If left or right is null this signifies there was no trough on that side
        if (leftTroughValue == null  ||  rightTroughValue == null)
            return false;

        int highestTrough = Math.max(leftTroughValue, rightTroughValue);
        return values[k] - highestTrough > threshold;
    }
    /**
     * As isSignificantPeak() but for troughs.
     */
    boolean isSignificantTrough(int k, int[] values)
    {
        Integer leftPeakValue = findPeakValue(k, values, false);
        Integer rightPeakValue = findPeakValue(k, values, true);
        // If left or right is null this signifies there was no peak on that side
        if (leftPeakValue == null  ||  rightPeakValue == null)
            return false;

        int lowestPeak = Math.min(leftPeakValue, rightPeakValue);
        return lowestPeak - values[k] > threshold;
    }

    /** @returns peak value (to left or right, as specified) or null if none */
    Integer findPeakValue(int i, int[] values, boolean toRight)
    {
        int value = values[i];
        int iDifferentValue = indexOfNextDifferentValue(i, values, toRight);
        if (iDifferentValue == -1)
            return null;
        int differentValue = values[iDifferentValue];
        if (differentValue < value)
            return null;
        // So values are increasing in the specified direction. Keep going until peak found.
        // differentValue is the current highest.

        do {
            int iNextDifferentValue = indexOfNextDifferentValue(iDifferentValue, values, toRight);
            if (iNextDifferentValue == -1)
                break;
            int nextDifferentValue = values[iNextDifferentValue];
            if (nextDifferentValue < differentValue)
                break;
            // Keep going
            iDifferentValue = iNextDifferentValue;
            differentValue = nextDifferentValue;
        } while (true);
        return differentValue;
    }
    /** @returns trough value (to left or right, as specified) or null if none */
    Integer findTroughValue(int i, int[] values, boolean toRight)
    {
        int value = values[i];
        int iDifferentValue = indexOfNextDifferentValue(i, values, toRight);
        if (iDifferentValue == -1)
            return null;
        int differentValue = values[iDifferentValue];
        if (differentValue > value)
            return null;
        // So values are diminishing in the specified direction. Keep going until trough found.
        // differentValue is the current lowest.

        do {
            int iNextDifferentValue = indexOfNextDifferentValue(iDifferentValue, values, toRight);
            if (iNextDifferentValue == -1)
                break;
            int nextDifferentValue = values[iNextDifferentValue];
            if (nextDifferentValue > differentValue)
                break;
            // Keep going
            iDifferentValue = iNextDifferentValue;
            differentValue = nextDifferentValue;
        } while (true);
        return differentValue;
    }

    /** @returns index of next different value (to left or right, as specified) or -1 if none */
    static int indexOfNextDifferentValue(int i, int[] values, boolean toRight)
    {
        final int len = values.length;
        if (len == 0)
            return -1;
        final int value = values[i];
        do {
            if (!toRight && --i == -1  ||  toRight && ++i == len)
                return -1;
        } while (values[i] == value);
        return i;
    }

}
