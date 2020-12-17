package com.easleydp.tempctrl.domain;

import java.util.List;
import java.util.Objects;

/**
 * Marries-up with the the contents of gyle.json
 */
public class GyleDto
{
    private String name;
    private TemperatureProfileDto temperatureProfile;
    private Long dtStarted;  // Null if not started. -ve value (typically -1) indicates beer fridge.
    private Long dtEnded;  // Null if not ended
    private Mode mode;

    public GyleDto() {}

    public GyleDto(String name, TemperatureProfileDto temperatureProfile, Long dtStarted, Long dtEnded, Mode mode) {
        if (name == null)
            throw new IllegalArgumentException("name is required");
        if (mode == null)
            throw new IllegalArgumentException("mode is required");
        validateTemperatureProfile(temperatureProfile);

        this.name = name;
        this.temperatureProfile = temperatureProfile;
        this.dtStarted = dtStarted;
        this.dtEnded = dtEnded;
        this.mode = mode;
    }

    private static void validateTemperatureProfile(TemperatureProfileDto profile)
    {
        if (profile == null) {
            throw new IllegalArgumentException("temperatureProfile is required");
        }

        List<PointDto> points = profile.getPoints();
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("Profile points cannot be " + (points == null ? "null" : "empty"));
        }
        int p0HoursSinceStart = points.get(0).getHoursSinceStart();
        if (p0HoursSinceStart != 0) {
            throw new IllegalArgumentException("First profile point isn't at t0: " + p0HoursSinceStart);
        }

        // See PointDto for further validation
    }

    public String getName()
    {
        return name;
    }
    public void setName(String name)
    {
        if (name == null)
            throw new IllegalArgumentException("name is required");
        this.name = name;
    }
    public TemperatureProfileDto getTemperatureProfile()
    {
        return temperatureProfile;
    }
    public void setTemperatureProfile(TemperatureProfileDto temperatureProfile)
    {
        validateTemperatureProfile(temperatureProfile);
        this.temperatureProfile = temperatureProfile;
    }
    public Long getDtStarted()
    {
        return dtStarted;
    }
    public void setDtStarted(Long dtStarted)
    {
        this.dtStarted = dtStarted;
    }
    public Long getDtEnded()
    {
        return dtEnded;
    }
    public void setDtEnded(Long dtEnded)
    {
        this.dtEnded = dtEnded;
    }
    public Mode getMode()
    {
        return mode;
    }
    public void setMode(Mode mode)
    {
    	// For the sake of backwards compatibility, if mode is not specified default to 'AUTO'
        this.mode = mode != null ? mode : Mode.AUTO;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof GyleDto)) {
            return false;
        }
        GyleDto gyleDto = (GyleDto) o;
        return Objects.equals(name, gyleDto.name)
            && Objects.equals(temperatureProfile, gyleDto.temperatureProfile)
            && Objects.equals(dtStarted, gyleDto.dtStarted)
            && Objects.equals(dtEnded, gyleDto.dtEnded)
            && Objects.equals(mode, gyleDto.mode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, temperatureProfile, dtStarted, dtEnded, mode);
    }

    @Override
    public String toString() {
        return "{" +
            " name='" + getName() + "'" +
            ", temperatureProfile=" + getTemperatureProfile() +
            ", dtStarted=" + getDtStarted() +
            ", dtEnded=" + getDtEnded() +
            ", mode=" + getMode() +
            "}";
    }
}
