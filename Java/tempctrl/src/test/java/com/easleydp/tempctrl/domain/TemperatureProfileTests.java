package com.easleydp.tempctrl.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TemperatureProfileTests {
    private TemperatureProfile profile;

    @BeforeEach
    public void beforeEachTest() {
        profile = new TemperatureProfile();
        profile.addPoint(0, 150);
        profile.addPoint(1, 200);
    }

    @Test
    public void testBasicInterpolation() {
        final int hourMillis = 1000 * 60 * 60;

        assertEquals(150, profile.getTargetTempAt(0));
        assertEquals(175, profile.getTargetTempAt(hourMillis / 2));
        assertEquals(200, profile.getTargetTempAt(hourMillis));
        assertEquals(200, profile.getTargetTempAt(hourMillis + 1));
        assertEquals(200, profile.getTargetTempAt(hourMillis * 2));
    }

    @Test
    public void testInvalid_millisSinceStartLessThanZero() {
        assertThrows(RuntimeException.class, () -> {
            profile.getTargetTempAt(-1);
        });
    }

    @Test
    public void testGetCrashStartPoint() {
        profile = new TemperatureProfile();
        profile.addPoint(0, 175);
        profile.addPoint(1, 0);
        assertNull(profile.getCrashStartPoint(),
                "Shouldn't be detected as crash because not preceded by a flat section.");

        profile = new TemperatureProfile();
        profile.addPoint(0, 175);
        profile.addPoint(1, 175);
        profile.addPoint(2, 0);
        assertEquals(profile.getPoints().get(1), profile.getCrashStartPoint(),
                "Second point should be start of crash.");

        profile.getPoints().get(2).setTargetTemp(150);
        assertNull(profile.getCrashStartPoint(), "Shouldn't be detected as crash because drop isn't deep enough.");
        profile.getPoints().get(2).setTargetTemp(0); // restore

        profile.getPoints().get(2).setHoursSinceStart(48);
        assertNull(profile.getCrashStartPoint(), "Shouldn't be detected as crash because gradient isn't steep enough.");
        profile.getPoints().get(2).setHoursSinceStart(2); // restore

        profile.addPoint(3, -1);
        assertEquals(profile.getPoints().get(1), profile.getCrashStartPoint(),
                "Second point should still be start of crash.");
    }
}
