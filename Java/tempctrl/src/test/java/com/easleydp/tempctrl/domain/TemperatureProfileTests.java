package com.easleydp.tempctrl.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

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

    @Test
    public void testGetCrashEndPoint() {
        profile = new TemperatureProfile();
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

    @Test
    /**
     * Prove a TemperatureProfile object (which is a TemperatureProfileDto) can be
     * serialised to a JSON representation of the DTO and then deserialised to a
     * TemperatureProfileDto without issue. Thereby prove there is no need for a
     * `toDto()` method.
     *
     * If this test fails it probably means you recently added a new field or getter
     * to the domain object and forgot to annotate `@JsonIgnore`.
     */
    public void testDomainObjectSerialisation() throws JsonProcessingException {
        profile = new TemperatureProfile();
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
