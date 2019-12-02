package com.easleydp.tempctrl.domain;

import static com.easleydp.tempctrl.domain.Smoother.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.springframework.util.Assert;

import com.easleydp.tempctrl.domain.Smoother.IntPropertyAccessor;

/**
 * Tests the Smoother class - removal of small fluctuations in a data series
 */
public class SmootherTests
{
    /*
     * First we test some of the Smoother internal helpers.
     */

    @Test
    public void indexOfNextDifferentValueToLeft()
    {
        final boolean toRight = false;

        assertEquals(-1, indexOfNextDifferentValue(0, new int[] {}, toRight));
        assertEquals(-1, indexOfNextDifferentValue(0, new int[] {1, 1, 1}, toRight));
        assertEquals(-1, indexOfNextDifferentValue(1, new int[] {1, 1, 1}, toRight));
        assertEquals(-1, indexOfNextDifferentValue(2, new int[] {1, 1, 1}, toRight));

        assertThrows(IndexOutOfBoundsException.class, () -> {
            indexOfNextDifferentValue(-1, new int[] {1, 1, 1}, toRight);
        });
        assertThrows(IndexOutOfBoundsException.class, () -> {
            indexOfNextDifferentValue(3, new int[] {1, 1, 1}, toRight);
        });

        assertEquals(0, indexOfNextDifferentValue(1, new int[] {1, 2, 3}, toRight));
        assertEquals(1, indexOfNextDifferentValue(2, new int[] {1, 2, 3}, toRight));
        assertEquals(0, indexOfNextDifferentValue(2, new int[] {1, 2, 2}, toRight));
        assertEquals(1, indexOfNextDifferentValue(2, new int[] {2, 2, 1}, toRight));
    }
    @Test
    public void indexOfNextDifferentValueToRight()
    {
        final boolean toRight = true;

        assertEquals(-1, indexOfNextDifferentValue(0, new int[] {}, toRight));
        assertEquals(-1, indexOfNextDifferentValue(2, new int[] {1, 1, 1}, toRight));
        assertEquals(-1, indexOfNextDifferentValue(1, new int[] {1, 1, 1}, toRight));
        assertEquals(-1, indexOfNextDifferentValue(0, new int[] {1, 1, 1}, toRight));

        assertThrows(IndexOutOfBoundsException.class, () -> {
            indexOfNextDifferentValue(-1, new int[] {1, 1, 1}, toRight);
        });
        assertThrows(IndexOutOfBoundsException.class, () -> {
            indexOfNextDifferentValue(3, new int[] {1, 1, 1}, toRight);
        });

        assertEquals(2, indexOfNextDifferentValue(1, new int[] {1, 2, 3}, toRight));
        assertEquals(1, indexOfNextDifferentValue(0, new int[] {1, 2, 3}, toRight));
        assertEquals(1, indexOfNextDifferentValue(0, new int[] {1, 2, 2}, toRight));
        assertEquals(2, indexOfNextDifferentValue(0, new int[] {2, 2, 1}, toRight));
    }

    @Test
    public void findPeakValueToLeft()
    {
        final boolean toRight = false;

        Smoother smoother = new Smoother(1);
        assertEquals(4, smoother.findPeakValue(4, new int[] {3, 4, 2, 2, 1}, toRight));
        assertEquals(4, smoother.findPeakValue(3, new int[] {3, 4, 2, 2, 1}, toRight));
        assertEquals(4, smoother.findPeakValue(2, new int[] {3, 4, 2, 2, 1}, toRight));
        assertNull(smoother.findPeakValue(1, new int[] {3, 4, 2, 2, 1}, toRight));
        assertNull(smoother.findPeakValue(0, new int[] {3, 4, 2, 2, 1}, toRight));

        assertNull(smoother.findPeakValue(2, new int[] {2, 1, 3}, toRight));
        assertEquals(2, smoother.findPeakValue(1, new int[] {2, 1, 3}, toRight));
        assertNull(smoother.findPeakValue(0, new int[] {2, 1, 3}, toRight));
    }
    @Test
    public void findPeakValueToRight()
    {
        final boolean toRight = true;

        Smoother smoother = new Smoother(1);
        assertEquals(4, smoother.findPeakValue(0, new int[] {3, 4, 2, 2, 1}, toRight));
        assertNull(smoother.findPeakValue(1, new int[] {3, 4, 2, 2, 1}, toRight));
        assertNull(smoother.findPeakValue(2, new int[] {3, 4, 2, 2, 1}, toRight));
        assertNull(smoother.findPeakValue(3, new int[] {3, 4, 2, 2, 1}, toRight));
        assertNull(smoother.findPeakValue(4, new int[] {3, 4, 2, 2, 1}, toRight));

        assertNull(smoother.findPeakValue(0, new int[] {2, 1, 3}, toRight));
        assertEquals(3, smoother.findPeakValue(1, new int[] {2, 1, 3}, toRight));
        assertNull(smoother.findPeakValue(2, new int[] {2, 1, 3}, toRight));

        assertEquals(3, smoother.findPeakValue(1, new int[] {2, 1, 3, 2}, toRight));
    }
    @Test
    public void findTroughValueToLeft()
    {
        final boolean toRight = false;

        Smoother smoother = new Smoother(1);
        assertEquals(1, smoother.findTroughValue(4, new int[] {4, 1, 2, 2, 3}, toRight));
        assertEquals(1, smoother.findTroughValue(3, new int[] {4, 1, 2, 2, 3}, toRight));
        assertEquals(1, smoother.findTroughValue(2, new int[] {4, 1, 2, 2, 3}, toRight));
        assertNull(smoother.findTroughValue(1, new int[] {4, 1, 2, 2, 3}, toRight));
        assertNull(smoother.findTroughValue(0, new int[] {4, 1, 2, 2, 3}, toRight));

        assertNull(smoother.findTroughValue(2, new int[] {2, 4, 3}, toRight));
        assertEquals(2, smoother.findTroughValue(1, new int[] {2, 4, 3}, toRight));
        assertNull(smoother.findTroughValue(0, new int[] {2, 4, 3}, toRight));
    }
    @Test
    public void findTroughValueToRight()
    {
        final boolean toRight = true;

        Smoother smoother = new Smoother(1);
        assertEquals(1, smoother.findTroughValue(0, new int[] {4, 1, 2, 2, 3}, toRight));
        assertNull(smoother.findTroughValue(1, new int[] {4, 1, 2, 2, 3}, toRight));
        assertNull(smoother.findTroughValue(2, new int[] {4, 1, 2, 2, 3}, toRight));
        assertNull(smoother.findTroughValue(3, new int[] {4, 1, 2, 2, 3}, toRight));
        assertNull(smoother.findTroughValue(4, new int[] {4, 1, 2, 2, 3}, toRight));

        assertNull(smoother.findTroughValue(0, new int[] {2, 4, 3}, toRight));
        assertEquals(3, smoother.findTroughValue(1, new int[] {2, 4, 3}, toRight));
        assertNull(smoother.findTroughValue(2, new int[] {2, 4, 3}, toRight));

        assertEquals(2, smoother.findTroughValue(1, new int[] {2, 4, 3, 2}, toRight));
    }

    @Test
    public void isPeak()
    {
        Smoother smoother = new Smoother(1);

        assertFalse(smoother.isSignificantPeak(0, new int[] {3}));

        assertFalse(smoother.isSignificantPeak(0, new int[] {1, 2, 3}));
        assertFalse(smoother.isSignificantPeak(1, new int[] {1, 2, 3}));
        assertFalse(smoother.isSignificantPeak(2, new int[] {1, 2, 3}));

        assertFalse(smoother.isSignificantPeak(0, new int[] {3, 2, 1}));
        assertFalse(smoother.isSignificantPeak(1, new int[] {3, 2, 1}));
        assertFalse(smoother.isSignificantPeak(2, new int[] {3, 2, 1}));

        assertFalse(smoother.isSignificantPeak(0, new int[] {2, 1, 3}));
        assertFalse(smoother.isSignificantPeak(1, new int[] {2, 1, 3}));
        assertFalse(smoother.isSignificantPeak(2, new int[] {2, 1, 3}));

        // Peak not above threshold
        assertFalse(smoother.isSignificantPeak(0, new int[] {1, 3, 2}));
        assertFalse(smoother.isSignificantPeak(1, new int[] {1, 3, 2}));
        assertFalse(smoother.isSignificantPeak(2, new int[] {1, 3, 2}));

        assertFalse(smoother.isSignificantPeak(0, new int[] {1, 4, 2}));
        assertTrue(smoother.isSignificantPeak(1, new int[] {1, 4, 2}));
        assertFalse(smoother.isSignificantPeak(2, new int[] {1, 4, 2}));
    }
    @Test
    public void isTrough()
    {
        Smoother smoother = new Smoother(1);

        assertFalse(smoother.isSignificantTrough(0, new int[] {3}));

        assertFalse(smoother.isSignificantTrough(0, new int[] {1, 2, 3}));
        assertFalse(smoother.isSignificantTrough(1, new int[] {1, 2, 3}));
        assertFalse(smoother.isSignificantTrough(2, new int[] {1, 2, 3}));

        assertFalse(smoother.isSignificantTrough(0, new int[] {3, 2, 1}));
        assertFalse(smoother.isSignificantTrough(1, new int[] {3, 2, 1}));
        assertFalse(smoother.isSignificantTrough(2, new int[] {3, 2, 1}));

        assertFalse(smoother.isSignificantTrough(0, new int[] {2, 4, 3}));
        assertFalse(smoother.isSignificantTrough(1, new int[] {2, 4, 3}));
        assertFalse(smoother.isSignificantTrough(2, new int[] {2, 4, 3}));

        // Trough not above threshold
        assertFalse(smoother.isSignificantTrough(0, new int[] {1, 0, 2}));
        assertFalse(smoother.isSignificantTrough(1, new int[] {1, 0, 2}));
        assertFalse(smoother.isSignificantTrough(2, new int[] {1, 0, 2}));

        assertFalse(smoother.isSignificantTrough(0, new int[] {1, -1, 2}));
        assertTrue(smoother.isSignificantTrough(1, new int[] {1, -1, 2}));
        assertFalse(smoother.isSignificantTrough(2, new int[] {1, -1, 2}));
    }

    /*
     * Test the main Smoother API.
     */

    @Test
    public void smallestArrayThatCanPossiblyBeSmoothed()
    {
        assertSmoothed(1,
                new int[] {1, 3, 2, 3},
                new int[] {1, 3, 3, 3});
    }

    @Test
    public void thresholdOfZeroIsInvalid()
    {
        assertThrows(IllegalArgumentException.class, () -> {
            new Smoother(0);
        });
    }

    @Test
    public void noSmoothingNeeded()
    {
        assertNotSmoothed(9,  new int[] {1});
        assertNotSmoothed(9,  new int[] {1, 1});
        assertNotSmoothed(9,  new int[] {1, 1, 1});
        assertNotSmoothed(9,  new int[] {1, 1, 1, 1});
        assertNotSmoothed(9,  new int[] {1, 1, 1, 1, 1});

        assertNotSmoothed(9,  new int[] {1, 2});
        assertNotSmoothed(9,  new int[] {2, 1});
        assertNotSmoothed(9,  new int[] {1, 2, 1});
        assertNotSmoothed(9,  new int[] {1, 2, 2});
        assertNotSmoothed(9,  new int[] {2, 2, 1});
        assertNotSmoothed(9,  new int[] {1, 2, 2, 1});
        assertNotSmoothed(9,  new int[] {1, 2, 2, 2});
        assertNotSmoothed(9,  new int[] {2, 2, 2, 2, 1});
        assertNotSmoothed(9,  new int[] {1, 2, 2, 2, 1});
        assertNotSmoothed(9,  new int[] {1, 2, 2, 2, 2});
        assertNotSmoothed(9,  new int[] {2, 2, 2, 2, 1});

        assertNotSmoothed(1,  new int[] {1, 5/*max*/, 1, 3/*diff>threshold*/, 1});
        assertNotSmoothed(2,  new int[] {1, 5/*max*/, 1, 4/*diff>threshold*/, 1});
        assertSmoothed(9,
                new int[] {1, 5/*max*/, 1, 5/*max again*/, 1},
                new int[] {1, 5/*max*/, 5, 5/*max again*/, 1});
    }

    @Test
    public void firstAndLastRippleNotSmoothed()
    {
        assertSmoothed(1,
                new int[] {1, 2, 1, 1, 2, 1},
                new int[] {1, 2, 2, 2, 2, 1});
        assertSmoothed(1,
                new int[] {1, 0, 1, 1, 0, 1},
                new int[] {1, 0, 0, 0, 0, 1});

        assertNotSmoothed(1,  new int[] {1, 2, 1, 1, 0, 1});

        assertSmoothed(1,
                new int[] {1, 2, 1, 2, 1, 2, 1},
                new int[] {1, 2, 2, 2, 2, 2, 1});

        assertSmoothed(1,
                new int[] {1, 2, 1, 1, 2, 1, 1, 0, 1, 1, 0, 1},
                new int[] {1, 2, 2, 2, 2, 1, 1, 1, 1, 1, 0, 1});
    }

    @Test
    public void insignificantPeaksAndTroughsSmoothedOut()
    {
        assertSmoothed(1,
                new int[] {1, 0, 1, 2, 1, 2, 1, 2, 1, 0, 1},
                new int[] {1, 0, 1, 1, 1, 1, 1, 2, 1, 0, 1});
        assertSmoothed(1,
                new int[] {1, 0, 1, 0, 1, 0, 1, 2, 1, 0, 1},
                new int[] {1, 0, 0, 0, 0, 0, 1, 2, 1, 0, 1});
    }

    @Test
    public void significantPeaksAndTroughsNotSmoothedOut()
    {
        assertNotSmoothed(1,  new int[] {1, 0, 1, 3, 1, 3, 1, 3, 1, 0, 1});
        assertNotSmoothed(1,  new int[] {1, 0, 2, 0, 2, 0, 2, 2, 1, 0, 1});
    }

    @Test
    public void smoothedOut()
    {
        assertSmoothed(1,
                new int[] {1, 9, 1, 3, 1, 2/*diff=threshold*/, 1/*back to 1*/, 9, 1},
                new int[] {1, 9, 1, 3, 1, 1/*smoothed*/,       1,              9, 1});

        // Same again but ends with crossing rather than hit
        assertSmoothed(1,
                new int[] {1, 9, 1, 3, 1, 2/*diff=threshold*/, 0/*crosses over 1*/, 9, 1},
                new int[] {1, 9, 1, 3, 1, 1/*smoothed*/,       0,                   9, 1});
    }

    @Test
    public void realistic()
    {
        int[] before = new int[]
                {150, 150, 151, 149/*min*/, 149, 150, 152, 154, 156, 156, 157, 156, 160, 162, 160, 162, 164, 164, 165, 165, 167, 165, 165, 168/*>threshold*/, 165, 166, 167, 167, 168, 169, 170/*max*/, 169};
        int[] after = new int[]
                {150, 150, 150, 149/*min*/, 149, 150, 152, 154, 156, 156, 156, 156, 160, 160, 160, 162, 164, 164, 165, 165, 165, 165, 165, 168/*>threshold*/, 165, 166, 167, 167, 168, 169, 170/*max*/, 169};
        assertSmoothed(2, before, after);

        // Smoothing an already smoothed column again should have no further affect.
        assertNotSmoothed(2, after);
    }

    @Test
    public void localExtremeNotSmoothed()
    {
        assertNotSmoothed(1,  new int[] {
                1, 2, 3, 4, 3, 2, 1,
                1, 2, 3, 4, 5, 4, 3, 2, 1
        });
    }

    /**
     * Most of the tests in this suite work with an int array for convenience. Check
     * records can be smoothed too.
     */
    @Test
    public void smoothRecords()
    {
        List<MyDto> records =
                Arrays.asList(new MyDto(1), new MyDto(3), new MyDto(2), new MyDto(3));
        List<MyDto> expectedSmoothedRecords =
                Arrays.asList(new MyDto(1), new MyDto(3), new MyDto(3), new MyDto(3));
        Assert.isTrue(records.size() == expectedSmoothedRecords.size(), "The two lists must be same length");

        assertNotEquals(expectedSmoothedRecords, records);

        new Smoother(1).smoothOutSmallFluctuations((List) records, new IntPropertyAccessor()
        {
            @Override
            public int getValue(Object record)
            {
                return ((MyDto) record).getFoo();
            }
            @Override
            public void setValue(Object record, int value)
            {
                ((MyDto) record).setFoo(value);
            }
        });

        assertEquals(expectedSmoothedRecords, records);
    }
    private static class MyDto
    {
        private int foo;

        public MyDto(int foo)
        {
            super();
            this.foo = foo;
        }

        public int getFoo()
        {
            return foo;
        }

        public void setFoo(int foo)
        {
            this.foo = foo;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            MyDto other = (MyDto) obj;
            if (foo != other.foo)
                return false;
            return true;
        }

        @Override  // Not used; just to suppress a warning
        public int hashCode()
        {
            return super.hashCode();
        }
    }

    /** Let's see how things work out using reflection. */
    @Test
    public void smoothRecords_reflection()
    {
        List<MyDto> records =
                Arrays.asList(new MyDto(1), new MyDto(3), new MyDto(2), new MyDto(3));
        List<MyDto> expectedSmoothedRecords =
                Arrays.asList(new MyDto(1), new MyDto(3), new MyDto(3), new MyDto(3));
        Assert.isTrue(records.size() == expectedSmoothedRecords.size(), "The two lists must be same length");

        assertNotEquals(expectedSmoothedRecords, records);

        new Smoother(1).smoothOutSmallFluctuations(records, "foo");

        assertEquals(expectedSmoothedRecords, records);
    }

    //@Test
    public void smoothRecords_performanceComparison_interface()
    {
        new Smoother(2).smoothOutSmallFluctuations((List) buildRecordForPerformanceTest(), new IntPropertyAccessor()
        {
            @Override
            public int getValue(Object record)
            {
                return ((MyDto) record).getFoo();
            }
            @Override
            public void setValue(Object record, int value)
            {
                ((MyDto) record).setFoo(value);
            }
        });
    }

    /** About x10 slower than smoothRecords_performanceComparison_interface() */
    //@Test
    public void smoothRecords_performanceComparison_reflection()
    {
        new Smoother(2).smoothOutSmallFluctuations(buildRecordForPerformanceTest(), "foo");
    }

    private List<MyDto> buildRecordForPerformanceTest()
    {
        // Assume 4 weeks at 1 record per minute
        final int recordCount = 60 * 24 * 7 * 4;
        List<MyDto> records = new ArrayList<>();
        for (int i = 0; i < recordCount; i++)
            records.add(new MyDto(randomInt(150, 160)));
        return records;
    }
    private static int randomInt(int from, int to)
    {
        return from + new Random().nextInt(to - from + 1);
    }


    private void assertNotSmoothed(int threshold, int[] values)
    {
        int[] copyValues = Arrays.copyOf(values, values.length);
        boolean noiseWasRemoved = new Smoother(threshold).smoothOutSmallFluctuations(values);
        assertEquals(false, noiseWasRemoved);
        assertArrayEquals(copyValues, values);
    }

    private void assertSmoothed(int threshold, int[] values, int[] expectedSmoothedValues)
    {
        Assert.isTrue(values.length == expectedSmoothedValues.length, "The two supplied arrays must be same length");
        Assert.isTrue(!Arrays.equals(values, expectedSmoothedValues), "The two supplied arrays should differ");

        boolean noiseWasRemoved = new Smoother(threshold).smoothOutSmallFluctuations(values);
        assertEquals(true, noiseWasRemoved);
        assertArrayEquals(expectedSmoothedValues, values);
    }

}
