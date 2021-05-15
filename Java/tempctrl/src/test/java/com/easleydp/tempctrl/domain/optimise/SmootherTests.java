package com.easleydp.tempctrl.domain.optimise;

import static com.easleydp.tempctrl.domain.optimise.Smoother.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.springframework.util.Assert;

import com.easleydp.tempctrl.domain.optimise.Smoother.IntPropertyAccessor;
import com.easleydp.tempctrl.domain.optimise.Smoother.Tip;

/**
 * Tests the Smoother class - removal of small fluctuations in a data series
 */
public class SmootherTests
{
    /*
     * First we test some of the Smoother internal helpers.
     */

    /* Tip tests */

    @Test
    public void tipVitalStatistics1()
    {
        int[] values = new int[] {0, 1, 2, 1, -1};
        Smoother smoother = new Smoother(1);

        assertThrows(IllegalArgumentException.class, () -> {
            smoother.new Tip(2, false, values);
        }, "The specified index should be that of a trough");

        Tip tip = smoother.new Tip(2, true, values);
        assertEquals(2, tip.height);
        assertEquals(3, tip.width);
        assertEquals(true, tip.peak);
        assertEquals(true, tip.significant);
    }

    @Test
    public void tipVitalStatistics2()
    {
        int[] values = new int[] {0, -1, -2, -1, 1};
        Smoother smoother = new Smoother(1);

        assertThrows(IllegalArgumentException.class, () -> {
            smoother.new Tip(2, true, values);
        }, "The specified index should be that of a peak");

        Tip tip = smoother.new Tip(2, false, values);
        assertEquals(2, tip.height);
        assertEquals(3, tip.width);
        assertEquals(false, tip.peak);
        assertEquals(true, tip.significant);
    }

    @Test
    public void tipVitalStatistics3()
    {
        {
            int[] values = new int[] {0, 1, 0};
            Smoother smoother = new Smoother(1);
            Tip tip = smoother.new Tip(1, true, values);
            assertFalse(tip.significant);
        }
        {
            int[] values = new int[] {0, -1, 0};
            Smoother smoother = new Smoother(1);
            Tip tip = smoother.new Tip(1, false, values);
            assertFalse(tip.significant);
        }
    }

    @Test
    public void tipWidthAndSignificance()
    {
        // Sanity check a narrow tip
        int[] values = new int[] {0, 1, 0};
        Smoother smoother = new Smoother(1);
        {
            Tip tip = smoother.new Tip(1, true, values);
            assertFalse(tip.significant);
        }

        // Wide tip
        values = new int[] {0, 1, 1, 0};
        {
            Tip tip = smoother.new Tip(1, true, values);
            assertEquals(2, tip.width);
            assertTrue(tip.significant);
        }
        // Since top is flat, we can specify any of multiple 'peak' values
        {
            Tip tip = smoother.new Tip(2, true, values);
            assertEquals(2, tip.width);
            assertTrue(tip.significant);
        }
    }

    //    @Test
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
        assertEquals(1, smoother.findNextTip(4, new int[] {3, 4, 2, 2, 1}, toRight).index);
        assertEquals(1, smoother.findNextTip(3, new int[] {3, 4, 2, 2, 1}, toRight).index);
        assertEquals(1, smoother.findNextTip(2, new int[] {3, 4, 2, 2, 1}, toRight).index);
        assertNull(smoother.findNextTip(1, new int[] {3, 4, 2, 2, 1}, toRight));
        assertNull(smoother.findNextTip(0, new int[] {3, 4, 2, 2, 1}, toRight));
    }
    @Test
    public void findPeakValueToRight()
    {
        final boolean toRight = true;

        Smoother smoother = new Smoother(1);
        assertEquals(1, smoother.findNextTip(0, new int[] {3, 4, 2, 2, 1}, toRight).index);
        assertNull(smoother.findNextTip(1, new int[] {3, 4, 2, 2, 1}, toRight));
        assertNull(smoother.findNextTip(2, new int[] {3, 4, 2, 2, 1}, toRight));
        assertNull(smoother.findNextTip(3, new int[] {3, 4, 2, 2, 1}, toRight));
        assertNull(smoother.findNextTip(4, new int[] {3, 4, 2, 2, 1}, toRight));
    }
    @Test
    public void findTroughValueToLeft()
    {
        final boolean toRight = false;

        Smoother smoother = new Smoother(1);
        assertEquals(1, smoother.findNextTip(4, new int[] {4, 1, 2, 2, 3}, toRight).index);
        assertEquals(1, smoother.findNextTip(3, new int[] {4, 1, 2, 2, 3}, toRight).index);
        assertEquals(1, smoother.findNextTip(2, new int[] {4, 1, 2, 2, 3}, toRight).index);
        assertNull(smoother.findNextTip(1, new int[] {4, 1, 2, 2, 3}, toRight));
        assertNull(smoother.findNextTip(0, new int[] {4, 1, 2, 2, 3}, toRight));
    }
    @Test
    public void findTroughValueToRight()
    {
        final boolean toRight = true;

        Smoother smoother = new Smoother(1);
        assertEquals(1, smoother.findNextTip(0, new int[] {4, 1, 2, 2, 3}, toRight).index);
        assertNull(smoother.findNextTip(1, new int[] {4, 1, 2, 2, 3}, toRight));
        assertNull(smoother.findNextTip(2, new int[] {4, 1, 2, 2, 3}, toRight));
        assertNull(smoother.findNextTip(3, new int[] {4, 1, 2, 2, 3}, toRight));
        assertNull(smoother.findNextTip(4, new int[] {4, 1, 2, 2, 3}, toRight));
    }

    @Test
    public void findNextTip()
    {
        Smoother smoother = new Smoother(1);

        // Peak to right
        assertNull(smoother.findNextTip(0, new int[] {1, 2, 2}, true));
        assertEquals(1, smoother.findNextTip(0, new int[] {1, 2, 1}, true).index);
        assertEquals(1, smoother.findNextTip(0, new int[] {1, 2, 2, 1}, true).index);

        // Peak to left
        assertNull(smoother.findNextTip(2, new int[] {1, 2, 2}, false));
        assertEquals(1, smoother.findNextTip(2, new int[] {1, 2, 1}, false).index);
        assertEquals(2, smoother.findNextTip(3, new int[] {1, 2, 2, 1}, false).index);

        // Trough to right
        assertNull(smoother.findNextTip(0, new int[] {2, 1, 1}, true));
        assertEquals(1, smoother.findNextTip(0, new int[] {2, 1, 2}, true).index);
        assertEquals(1, smoother.findNextTip(0, new int[] {2, 1, 1, 2}, true).index);

        // Trough to left
        assertNull(smoother.findNextTip(2, new int[] {2, 1, 1}, false));
        assertEquals(1, smoother.findNextTip(2, new int[] {2, 1, 2}, false).index);
        assertEquals(2, smoother.findNextTip(3, new int[] {2, 1, 1, 2}, false).index);
    }

    @Test
    /**
     * Reproduce a bug where an assertion got caught out. Width and height of last
     * tip are liable to change due to smoothing that's happened to the immediate left.
     */
    public void testSmoothingAltersHeightOfLastTip()
    {
        assertSmoothed(2,
                new int[] {1, 0, 1, 0, 2, 0},  // lastTip height is 2
                new int[] {1, 1, 1, 1, 2, 0}); // lastTip height is now 1

        // Dubious whether that last tip (2) should have been smoothed (to 1) but the current
        // rule is that the last tip is never smoothed (see test lastRippleNotSmoothed()).
    }

    @Test
    /**
     * Reproduce a bug where an assertion got caught out. Width and height of last
     * tip are liable to change due to smoothing that's happened to the immediate left.
     */
    public void testSmoothingAltersWidthOfLastTip()
    {
        assertSmoothed(2,
                new int[] {2, 1, 3, 3, 2, 1},  // lastTip width is 3
                new int[] {2, 2, 3, 3, 2, 1}); // lastTip width is now 2
    }

    @Test
    /**
     * Stress test the invariants (assertions) built into the Smoother logic.
     * Note: Consider using any failures as the basis for further tests, such as
     * testSmoothingAltersHeightOfLastTip() & testSmoothingAltersWidthOfLastTip().
     */
    public void testStressSmoother()
    {
        int[] values = new int[0];
        Smoother smoother = new Smoother(2);
        List<String> failures = new ArrayList<>();
        int tries = 100_000;
        for (int n = 0; n < tries; n++) {
            try {
                // Create some random values
                values = new int[randomInt(6, 10)];
                for (int i = 0; i < values.length; i++) {
                    values[i] = randomInt(0, 3);
                }
                // Subject the code under test to the random data
                smoother.smoothOutSmallFluctuations(values.clone());
            } catch (IllegalStateException ex) {
                failures.add("\n\"" + ex.getMessage() + "\". These values broke it: " + Arrays.toString(values));
            }
            if (failures.size() > 10) break;
        }
        if (!failures.isEmpty()) {
            fail(org.apache.commons.lang3.StringUtils.join(failures));
        }
    }

    @Test
    public void flattenTip()
    {
        assertTipFlattened(
                2, false,
                new int[] {1, 5, 1, 5, 1},
                new int[] {1, 5, 5, 5, 1});

        assertTipFlattened(
                1, true,
                new int[] {1, 5, 2},
                new int[] {1, 2, 2});

        assertTipFlattened(
                1, false,
                new int[] {4, 1, 5},
                new int[] {4, 4, 5});

        // Wide tip
        assertTipFlattened(
                2, true,
                new int[] {1, 5, 5, 5, 2},
                new int[] {1, 2, 2, 2, 2});
    }

    private void assertTipFlattened(int index, boolean isPeak, int[] values, int[] expectedValuesAfter)
    {
        Smoother smoother = new Smoother(1);
        Tip tip = smoother.new Tip(index, isPeak, values);
        smoother.flattenTip(tip, values);
        assertArrayEquals(expectedValuesAfter, values);
    }

    /*
     * Test the main Smoother API.
     */

    @Test
    public void smallestArrayThatCanPossiblyBeSmoothed()
    {
        final int tooShortToNeedSmoothing = 3;

        assertSmoothed(1,
                new int[] {1, 2, 1, 3},
                new int[] {1, 1, 1, 3});

        final int tries = 100000;
        for (int i = 0; i < tries; i++)
        {
            int[] values = new int[tooShortToNeedSmoothing];
            for (int j = 0; j < tooShortToNeedSmoothing; j++)
                values[j] = randomInt(1, 3);
            assertNotSmoothed(10, values);
        }
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
                new int[] {1, 5, 1, 5, 1},
                new int[] {1, 1, 1, 5, 1});
    }

    @Test
    /**
     * Proves the rule that the last ripple is not smoothed. Rationale: If the height
     * happens to be dictated by the RHS then, if/when more points are appended, the
     * height could increase. (Maybe likewise with width?) Therefore, could possibly
     * improve this; if the height dictated by the LHS makes it insignificant then it
     * could safely be smoothed.
     */
    public void lastRippleNotSmoothed()
    {
        assertSmoothed(1,
                new int[] {1, 2, 1, 1, 2, 1},
                new int[] {1, 1, 1, 1, 2, 1});
        assertSmoothed(1,
                new int[] {1, 0, 1, 1, 0, 1},
                new int[] {1, 1, 1, 1, 0, 1});

        assertNotSmoothed(1,  new int[] {1, 1, 1, 1, 0, 1});

        assertSmoothed(1,
                new int[] {1, 2, 1, 2, 1, 2, 1},
                new int[] {1, 1, 1, 1, 1, 2, 1});

        assertSmoothed(1,
                new int[] {1, 2, 1, 1, 2, 1, 1, 0, 1, 1, 0, 1},
                new int[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1});
    }

    @Test
    public void insignificantPeaksAndTroughsSmoothedOut()
    {
        assertSmoothed(1,
                new int[] {1, 0, 1, 2, 1, 2, 1, 2, 1, 0, 1},
                new int[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1});
        assertSmoothed(1,
                new int[] {1, 0, 1, 0, 1, 0, 1, 2, 1, 0, 1},
                new int[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1});
    }

    @Test
    public void significantPeaksAndTroughsNotSmoothedOut()
    {
        assertNotSmoothed(1,  new int[] {0, 1, 3, 1, 3, 1, 3, 1, 0, 1});
        assertNotSmoothed(1,  new int[] {0, 2, 0, 2, 0, 2, 2, 1, 0, 1});
    }

    @Test
    public void smoothedOut()
    {
        assertSmoothed(1,
                new int[] {9, 1, 3, 1, 2, 1, 9, 1},
                new int[] {9, 1, 3, 2, 2, 1, 9, 1});

        assertSmoothed(1,
                new int[] {1, 9, 1, 3, 1, 2, 1, 9, 1},
                new int[] {1, 9, 1, 3, 2, 2, 1, 9, 1});

        // Same again but ends with crossing rather than hit
        assertSmoothed(1,
                new int[] {1, 9, 1, 3, 1, 2, 0, 9, 1},
                new int[] {1, 9, 1, 3, 2, 2, 0, 9, 1});
    }

    @Test
    public void realistic()
    {
        int[] before = new int[]
                {150, 150, 151, 149, 149, 150, 152, 154, 156, 156, 157, 156, 160, 162, 160, 162, 164, 164, 165, 165, 167, 165, 165, 168, 165, 166, 167, 167, 168, 169, 170, 169};
        int[] after = new int[]
                {150, 150, 150, 150, 150, 150, 152, 154, 156, 156, 156, 156, 160, 160, 160, 162, 164, 164, 165, 165, 165, 165, 165, 168, 165, 166, 167, 167, 168, 169, 170, 169};
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
                Arrays.asList(new MyDto(1), new MyDto(2), new MyDto(1), new MyDto(3));
        List<MyDto> expectedSmoothedRecords =
                Arrays.asList(new MyDto(1), new MyDto(1), new MyDto(1), new MyDto(3));
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
        @Override
        public int hashCode()
        {
            return foo;
        }
    }

    /** Let's see how things work out using reflection. */
    @Test
    public void smoothRecords_reflection()
    {
        List<MyDto> records =
                Arrays.asList(new MyDto(1), new MyDto(2), new MyDto(1), new MyDto(3));
        List<MyDto> expectedSmoothedRecords =
                Arrays.asList(new MyDto(1), new MyDto(1), new MyDto(1), new MyDto(3));
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
