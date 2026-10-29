package com.easleydp.tempctrl.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.easleydp.tempctrl.dto.TemperatureProfileDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class TemperatureProfileTests {
    final int hourMillis = 1000 * 60 * 60;

    @Test
    public void testBasicInterpolation() {
        TemperatureProfile profile = new TemperatureProfile();
        profile.addPoint(0, 0);
        profile.addPoint(4, 400);
        // profile.addPoint(8, 400); redundant point

        // Without smoothing
        TemperatureProfile straightProfile = new TemperatureProfile(profile, 0);
        assertEquals(0, straightProfile.getTargetTempAt(0));
        assertEquals(100, straightProfile.getTargetTempAt(hourMillis * 1));
        assertEquals(200, straightProfile.getTargetTempAt(hourMillis * 2));
        assertEquals(300, straightProfile.getTargetTempAt(hourMillis * 3));
        assertEquals(400, straightProfile.getTargetTempAt(hourMillis * 4));
        assertEquals(400, straightProfile.getTargetTempAt(hourMillis * 5));

        // With smoothing. Note, the usual 3 hours is capped because our points are only
        // 4 hours apart (half of 4h is 2h), so converges to 400 after 2 hours.
        assertEquals(0, profile.getTargetTempAt(0));
        assertEquals(100, profile.getTargetTempAt(hourMillis * 1));
        assertEquals(200, profile.getTargetTempAt(hourMillis * 2));
        assertEquals(288, profile.getTargetTempAt(hourMillis * 3));
        assertEquals(350, profile.getTargetTempAt(hourMillis * 4));
        assertEquals(388, profile.getTargetTempAt(hourMillis * 5));
        assertEquals(400, profile.getTargetTempAt(hourMillis * 6));
    }

    @Test
    public void testCutsRightAngleCornerWithoutPassingThroughWaypoint() {
        TemperatureProfile profile = new TemperatureProfile();
        profile.addPoint(0, 100);
        profile.addPoint(6, 220);
        profile.addPoint(12, 220);
        TemperatureProfile straightProfile = new TemperatureProfile(profile, 0);

        assertEquals(160, profile.getTargetTempAt(3 * hourMillis));
        assertEquals(186, profile.getTargetTempAt(4 * hourMillis + hourMillis / 2));
        assertEquals(205, profile.getTargetTempAt(6 * hourMillis));
        assertEquals(216, profile.getTargetTempAt(7 * hourMillis + hourMillis / 2));
        assertEquals(220, profile.getTargetTempAt(9 * hourMillis));

        int previousTarget = profile.getTargetTempAt(3 * hourMillis);
        for (int hour = 3; hour <= 9; hour++) {
            int target = profile.getTargetTempAt(hour * hourMillis);
            assertTrue(target >= previousTarget, "Corner-cut target should not reverse direction");
            assertTrue(target <= straightProfile.getTargetTempAt(hour * hourMillis),
                    "Rising-to-flat fillet should stay below the straight profile");
            previousTarget = target;
        }
        assertTrue(profile.getTargetTempAt(6 * hourMillis) < 220,
                "The rounded profile must cut the corner instead of reaching the waypoint");
    }

    @Test
    public void testCutsFinalWaypointCornerIntoHeldTemperature() {
        TemperatureProfile profile = new TemperatureProfile();
        profile.addPoint(0, 100);
        profile.addPoint(10, 200);

        assertTrue(profile.getTargetTempAt(10 * hourMillis) < 200);
        assertTrue(profile.getTargetTempAt(11 * hourMillis) < 200);
        assertTrue(profile.getTargetTempAt(12 * hourMillis) < 200);
        assertTrue(profile.getTargetTempAt(13 * hourMillis) == 200);
    }

    @Test
    public void testFallingCornerCutDoesNotUndershootTheHeldTemperature() {
        TemperatureProfile profile = new TemperatureProfile();
        profile.addPoint(0, 220);
        profile.addPoint(6, 100);
        // profile.addPoint(12, 100); Redundant point
        TemperatureProfile straightProfile = new TemperatureProfile(profile, 0);

        assertEquals(115, profile.getTargetTempAt(6 * hourMillis));
        for (int hour = 3; hour <= 9; hour++) {
            assertTrue(profile.getTargetTempAt(hour * hourMillis) >= 100,
                    "Falling-to-flat fillet must not go below the held temperature");
            assertTrue(profile.getTargetTempAt(hour * hourMillis) >= straightProfile.getTargetTempAt(hour * hourMillis),
                    "Falling-to-flat fillet should stay inside the corner");
        }
        assertEquals(100, profile.getTargetTempAt(9 * hourMillis));
    }

    @Test
    public void testZeroTransitionWindowPreservesStraightProfile() {
        TemperatureProfile profile = new TemperatureProfile();
        profile.addPoint(0, 100);
        profile.addPoint(6, 220);
        // profile.addPoint(12, 220); redundant point
        profile = new TemperatureProfile(profile, 0);

        assertEquals(190, profile.getTargetTempAt(4 * hourMillis + hourMillis / 2));
        assertEquals(220, profile.getTargetTempAt(6 * hourMillis));
    }

    @Test
    public void testStepChangeUpwards() {
        TemperatureProfile profile = new TemperatureProfile();
        profile.addPoint(0, 0);
        profile.addPoint(6, 0);
        profile.addPoint(6, 100);
        // profile.addPoint(12, 100);

        assertTrue(profile.getTargetTempAt(3 * hourMillis) == 0);
        assertTrue(profile.getTargetTempAt(Math.round(4.5 * hourMillis)) < 25);
        assertTrue(profile.getTargetTempAt(6 * hourMillis) == 50);
        assertTrue(profile.getTargetTempAt(Math.round(7.5 * hourMillis)) > 75);
        assertTrue(profile.getTargetTempAt(9 * hourMillis) == 100);

        // When operating without smoothing, it's arbitrary whether to go with one
        // temperature or the other (but not the mid-point)
        TemperatureProfile straightProfile = new TemperatureProfile(profile, 0);
        assertTrue(straightProfile.getTargetTempAt(3 * hourMillis) == 0);
        assertTrue(straightProfile.getTargetTempAt(Math.round(4.5 * hourMillis)) == 0);
        int target = straightProfile.getTargetTempAt(6 * hourMillis);
        assertTrue(target == 0 || target == 100);
        assertTrue(straightProfile.getTargetTempAt(Math.round(7.5 * hourMillis)) == 100);
        assertTrue(straightProfile.getTargetTempAt(9 * hourMillis) == 100);
    }

    @Test
    public void testStepChangeDownwards() {
        TemperatureProfile profile = new TemperatureProfile();
        profile.addPoint(0, 100);
        profile.addPoint(6, 100);
        profile.addPoint(6, 0);
        // profile.addPoint(12, 0);

        assertTrue(profile.getTargetTempAt(3 * hourMillis) == 100);
        assertTrue(profile.getTargetTempAt(Math.round(4.5 * hourMillis)) > 75);
        assertTrue(profile.getTargetTempAt(6 * hourMillis) == 50);
        assertTrue(profile.getTargetTempAt(Math.round(7.5 * hourMillis)) < 25);
        assertTrue(profile.getTargetTempAt(9 * hourMillis) == 0);

        // When operating without smoothing, it's arbitrary whether to go with one
        // temperature or the other (but not the mid-point)
        TemperatureProfile straightProfile = new TemperatureProfile(profile, 0);
        assertTrue(straightProfile.getTargetTempAt(3 * hourMillis) == 100);
        assertTrue(straightProfile.getTargetTempAt(Math.round(4.5 * hourMillis)) == 100);
        int target = straightProfile.getTargetTempAt(6 * hourMillis);
        assertTrue(target == 0 || target == 100);
        assertTrue(straightProfile.getTargetTempAt(Math.round(7.5 * hourMillis)) == 0);
        assertTrue(straightProfile.getTargetTempAt(9 * hourMillis) == 0);
    }

    @Test
    public void testmillisSinceStartLessThanZero() {
        TemperatureProfile profile = new TemperatureProfile();
        profile.addPoint(0, 150);
        profile.addPoint(1, 200);

        assertEquals(150, profile.getTargetTempAt(-1));
    }

    @Test
    public void testRejectsInvalidProfilePoints() {
        TemperatureProfileDto invalidProfile = new TemperatureProfileDto();
        assertThrows(IllegalArgumentException.class, () -> invalidProfile.setPoints(null));
        assertThrows(IllegalArgumentException.class, () -> invalidProfile.setPoints(java.util.Collections.emptyList()));

        TemperatureProfile validProfile = new TemperatureProfile();
        validProfile.addPoint(0, 150);
        validProfile.addPoint(1, 200);

        assertThrows(IllegalArgumentException.class, () -> {
            validProfile.setPoints(java.util.Arrays.asList(new com.easleydp.tempctrl.dto.PointDto(1, 150),
                    new com.easleydp.tempctrl.dto.PointDto(0, 160)));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            validProfile.setPoints(java.util.Arrays.asList(new com.easleydp.tempctrl.dto.PointDto(1, 150),
                    new com.easleydp.tempctrl.dto.PointDto(2, 160)));
        });
    }

    @Test
    public void testGetCrashStartPoint() {
        TemperatureProfile profile = new TemperatureProfile();
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

    @Test
    public void testGetCrashEndPoint() {
        TemperatureProfile profile = new TemperatureProfile();
        profile.addPoint(0, 175);
        profile.addPoint(1, 0);
        assertNull(profile.getCrashEndPoint(),
                "Shouldn't be detected as crash because not preceded by a flat section.");

        profile = new TemperatureProfile();
        profile.addPoint(0, 175);
        profile.addPoint(1, 175);
        profile.addPoint(2, 0);
        assertEquals(profile.getPoints().get(2), profile.getCrashEndPoint(), "Last point should be end of crash.");

        profile.getPoints().get(2).setTargetTemp(150);
        assertNull(profile.getCrashEndPoint(), "Shouldn't be detected as crash because drop isn't deep enough.");
        profile.getPoints().get(2).setTargetTemp(0); // restore

        profile.getPoints().get(2).setHoursSinceStart(48);
        assertNull(profile.getCrashEndPoint(), "Shouldn't be detected as crash because gradient isn't steep enough.");
        profile.getPoints().get(2).setHoursSinceStart(2); // restore

        profile.addPoint(3, -1);
        assertEquals(profile.getPoints().get(3), profile.getCrashEndPoint(),
                "Last point should still be end of crash.");
    }

    /**
     * Prove a TemperatureProfile object (which is a TemperatureProfileDto) can be
     * serialised to a JSON representation of the DTO and then deserialised to a
     * TemperatureProfileDto without issue. Thereby prove there is no need for a
     * `toDto()` method.
     *
     * If this test fails it probably means you recently added a new field or getter
     * to the domain object and forgot to annotate `@JsonIgnore`.
     */
    @Test
    public void testDomainObjectSerialisation() throws JsonProcessingException {
        TemperatureProfile profile = new TemperatureProfile();
        profile.addPoint(0, 175);
        profile.addPoint(1, 0);

        // Serialise
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        String json = writer.writeValueAsString(profile);

        // Deserialise
        TemperatureProfileDto dto = mapper.readValue(json, TemperatureProfileDto.class);

        assertTrue(dto.equals(profile));
    }
}
