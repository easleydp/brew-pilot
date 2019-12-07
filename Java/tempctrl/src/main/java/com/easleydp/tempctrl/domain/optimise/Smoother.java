package com.easleydp.tempctrl.domain.optimise;

import java.util.List;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.util.Assert;

/**
 * Removes small fluctuations in a data series.
 *
 * Points have an index and a value. Assumes the index represents some fixed sampling frequency,
 * e.g. every 1 minute. This is important because the width of peaks/troughs is considered when
 * assessing their significance.
 *
 * Terminology:
 *   - NextMinLeft - If there are points descending to the left (possibly after a flat section),
 *     the lowest point to the left. Otherwise null. Note: this is not necessarily a trough
 *     since could be start of array.
 *   - NextMinRight - As NextMinLeft but to the right. Note: the lowest point to the right is
 *     not necessarily a trough since could be end of array.
 *   - NextMaxLeft, NextMaxRight - as NextMinLeft, NextMinRight
 *   - Peak - point where the slope descends on both sides, i.e with a non-null NextMinLeft &
 *     NextMinRight.
 *   - Trough - as peak, but slope ascends on both sides.
 *   - Tip - a peak or a trough
 *   - Tip height: For a peak, peak value - Max(NextMinLeft, NextMinRight).
 *   - For a trough, Min(NextMaxLeft, NextMaxRight) - trough value
 *   - Tip width - the number of points that would be flattened (>= 1)
 *   - Tip significance - a tip is insignificant if its height and width are sufficiently low
 *     (according to the thresholdHeight and thresholdWidths supplied to the Smoother
 *     constructor) to be flattened. That is,
 *     height > thresholdHeight  ||  width > thresholdWidths[height - 1]
 */
public class Smoother
{
    private int thresholdHeight;
    private int[] thresholdWidths;

    /**
     * @param thresholdHeight - any tips with height <= to this value may be flattened (depending
     *      on the tip width), hence must be > 0 (or no point calling this routine).
     * @param thresholdWidths - for each height value 1..thresholdHeight, a max width (width being
     *      the number of points that would be flattened). Each successive value must be <= the
     *      preceding value. The final value is typically 1. Example:
     *      thresholdHeight: 5, thresholdWidths: [3, 2, 1, 1, 1]
     *      So, spikes of height 3, 4 & 5 will only be flattened if width is 1; ripple of height 1
     *      will only be flattened if width is <= 3.
     */
    public Smoother(int thresholdHeight, int[] thresholdWidths)
    {
        // Doesn't make sense to specify a threshold of 0 as that equates to no smoothing
        Assert.isTrue(thresholdHeight > 0, "threshold must be greater than zero");

        Assert.isTrue(thresholdHeight == thresholdWidths.length, "thresholdHeight should equal thresholdWidths.length");

        int prevWidth = Integer.MAX_VALUE;
        for (int i = 0; i < thresholdHeight; i++)
        {
            int width = thresholdWidths[i];
            Assert.isTrue(width > 0, "each width should be > 0");
            Assert.isTrue(width <= prevWidth, "each width should be <= the previous width");
        }

        this.thresholdHeight = thresholdHeight;
        this.thresholdWidths = thresholdWidths;
    }
    /** Convenience ctor giving (e.g.) for thresholdHeight of 3, thresholdWidths: [3, 2, 1] */
    Smoother(int thresholdHeight)
    {
        this(thresholdHeight, getDefaultWidths(thresholdHeight));
    }
    private static int[] getDefaultWidths(int height)
    {
        int len = height;
        int[] widths = new int[height];
        for (int i = 0; i < height; i++)
            widths[i] = len--;
        return widths;
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
     * Note that the last fluctuation is never smoothed because, should further values be appended
     * to the series, it might become significant.
     *
     * @param records - the records, one column of which needs to be smoothed
     * @param intAccessor - an IntPropertyAccessor impl to get & set the column of interest.
     */
    public void smoothOutSmallFluctuations(List<Object> records, final IntPropertyAccessor intAccessor)
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
    public static interface IntPropertyAccessor
    {
        int getValue(Object record);
        void setValue(Object record, int value);
    }

    /**
     * Or just use reflection? Seems to be about x10 slower than using the IntPropertyAccessor version.
     */
    void smoothOutSmallFluctuations(List<?> records, String propertyName)
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
        // whereas 4 can, e.g. (w/ threshold=1) [1, 2, 1, 3] => [1, 1, 1, 3]
        // See test smallestArrayThatCanPossiblyBeSmoothed()
        if (len < 4)
            return false;

        Tip lastTip = findNextTip(len - 1, values, false);
        if (lastTip == null)
            return false;

        boolean noiseWasRemoved = false;
        for (Tip tip = findNextInsignificantTip(0, values, true);  // First insignificant tip
                tip != null && !tip.isEffectivelyEqualTo(lastTip, values);  // Avoid removing the last tip
                tip = findNextInsignificantTip(tip.index, values, true))  // Next insignificant tip
        {
            flattenTip(tip, values);
            noiseWasRemoved = true;
        }
        return noiseWasRemoved;
    }

    void flattenTip(Tip tip, int[] values)
    {
        if (tip.peak)
        {
            int floor = tip.value - tip.height;
            int i = findNextLocalMin(tip.index, values, false).index;
            int j = findNextLocalMin(tip.index, values, true).index;
            while (++i < j)
                if (values[i] > floor)
                    values[i] = floor;
        }
        else  // trough
        {
            int ceiling = tip.value + tip.height;
            int i = findNextLocalMax(tip.index, values, false).index;
            int j = findNextLocalMax(tip.index, values, true).index;
            while (++i < j)
                if (values[i] < ceiling)
                    values[i] = ceiling;
        }
    }

    /**
     * @returns index & value of next local max (seeking to left or right, as specified), or null if none.
     * Note: A max is not necessarily a tip; e.g. if seeking rightwards, could be a rising value (or last
     * flat after rising) at end of the values array.
     */
    Point findNextLocalMax(int i, int[] values, boolean toRight)
    {
        int value = values[i];
        int iDifferentValue = indexOfNextDifferentValue(i, values, toRight);
        if (iDifferentValue == -1)
            return null;
        int differentValue = values[iDifferentValue];
        if (differentValue < value)
            return null;
        // So values are increasing in the specified direction. We will return a non-null result.
        // Keep going until peak found. differentValue is the current highest.

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
        return new Point(iDifferentValue, differentValue);
    }

    /** As findNextLocalMaxima but for looks for a local min */
    Point findNextLocalMin(int i, int[] values, boolean toRight)
    {
        int value = values[i];
        int iDifferentValue = indexOfNextDifferentValue(i, values, toRight);
        if (iDifferentValue == -1)
            return null;
        int differentValue = values[iDifferentValue];
        if (differentValue > value)
            return null;
        // So values are diminishing in the specified direction. We will return a non-null result.
        // Keep going until trough found. differentValue is the current lowest.

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
        return new Point(iDifferentValue, differentValue);
    }

    Tip findNextTip(int i, int[] values, boolean toRight)
    {
        {
            Point nextLocalMax = findNextLocalMax(i, values, toRight);
            if (nextLocalMax != null)
            {
                // If there's a point to the right (or left) that's smaller then it's a peak
                if (findNextLocalMin(nextLocalMax.index, values, toRight) != null)
                    return new Tip(nextLocalMax.index, true, values);
            }
        }
        {
            Point nextLocalMin = findNextLocalMin(i, values, toRight);
            if (nextLocalMin != null)
            {
                // If there's a point to the right (or left) that's larger then it's a peak
                if (findNextLocalMax(nextLocalMin.index, values, toRight) != null)
                    return new Tip(nextLocalMin.index, false, values);
            }
        }

        return null;
    }

    private Tip findNextInsignificantTip(int i, int[] values, boolean toRight)
    {
        do {
            Tip tip = findNextTip(i, values, toRight);
            if (tip == null)
                return null;
            if (!tip.significant)
                return tip;
            i = tip.index;
        } while (true);
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

    static class Point
    {
        final int index;
        final int value;
        public Point(int index, int value)
        {
            this.index = index;
            this.value = value;
        }
    }

    class Tip extends Point
    {
        final boolean peak;
        final int width;
        final int height;
        final boolean significant;

        /** Note: `values` is only used on construction for calculating width & height; no reference is retained. */
        public Tip(int index, boolean peak, int[] values)
        {
            super(index, values[index]);
            this.peak = peak;

            if (peak)
            {
                Point pLeft  = findNextLocalMin(index, values, false);
                Point pRight = findNextLocalMin(index, values, true);
                Assert.isTrue(pLeft != null  &&  pRight != null, "The specified index should be that of a peak");
                int floor = Math.max(pLeft.value, pRight.value);
                height = value - floor;

                // Width is the number of points between pLeft & pRights that would be flattened
                int w = 0;
                for (int i = pLeft.index + 1; i < pRight.index; i++)
                    if (values[i] > floor)
                        w++;
                width = w;
            }
            else  // trough
            {
                Point pLeft  = findNextLocalMax(index, values, false);
                Point pRight = findNextLocalMax(index, values, true);
                Assert.isTrue(pLeft != null  &&  pRight != null, "The specified index should be that of a trough");
                int floor = Math.min(pLeft.value, pRight.value);
                height = floor - value;

                // Width is the number of points between pLeft & pRights that would be flattened
                int w = 0;
                for (int i = pLeft.index + 1; i < pRight.index; i++)
                    if (values[i] < floor)
                        w++;
                width = w;
            }

            this.significant = height > thresholdHeight  ||  width > thresholdWidths[height - 1];
        }

        /**
         * A flat-topped peak (or flat-bottomed trough) may have any index along the flat
         * region (though typically first or last).
         */
        public boolean isEffectivelyEqualTo(Tip other, int[] values)
        {
            if (value != other.value)
                return false;

            // If every point between the two tips is the same value, they're equal.
            for (int i = index; i < other.index; i++)
                if (values[i] != value)
                    return false;

            // Having determined that it's effectively the same tip, it just remains to confirm that
            // the other is equal in other respects too. (This is by way of an assertion since it would
            // be a logic error if this ever failed.)
            Assert.state(value == other.value, "value should be same");
            Assert.state(width == other.width, "width should be same");
            Assert.state(height == other.height, "height should be same");
            Assert.state(peak == other.peak, "peak should be same");

            return true;
        }
    }

}
