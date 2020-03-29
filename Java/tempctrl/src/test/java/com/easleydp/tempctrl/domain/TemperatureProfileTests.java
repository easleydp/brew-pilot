package com.easleydp.tempctrl.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TemperatureProfileTests
{
    private TemperatureProfile profile;

    @BeforeEach
    public void beforeEachTest()
    {
        profile = new TemperatureProfile();
        profile.addPoint(0, 150);
        profile.addPoint(1, 200);
    }

    @Test
    public void testBasicInterpolation()
    {
        final int hourMillis = 1000 * 60 * 60;

        assertEquals(150, profile.getTargetTempAt(0));
        assertEquals(175, profile.getTargetTempAt(hourMillis / 2));
        assertEquals(200, profile.getTargetTempAt(hourMillis));
        assertEquals(200, profile.getTargetTempAt(hourMillis + 1));
        assertEquals(200, profile.getTargetTempAt(hourMillis * 2));
    }

    @Test
    public void testInvalid_millisSinceStartLessThanZero()
    {
        assertThrows(RuntimeException.class, () -> {
            profile.getTargetTempAt(-1);
        });
    }
}
