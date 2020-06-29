package com.easleydp.tempctrl.domain;

/**
 * Marries-up with the the contents of gyle.json
 */
public class GyleDto
{
    private String name;
    private TemperatureProfileDto temperatureProfile;
    private Long dtStarted;  // Null if not started
    private Long dtEnded;  // Null if not ended
    private Mode mode;

    public GyleDto() {}

    public GyleDto(String name, TemperatureProfileDto temperatureProfile, Long dtStarted, Long dtEnded, Mode mode) {
        if (name == null)
            throw new IllegalArgumentException("name is required");
        if (temperatureProfile == null)
            throw new IllegalArgumentException("temperatureProfile is required");
        if (mode == null)
            throw new IllegalArgumentException("mode is required");

        this.name = name;
        this.temperatureProfile = temperatureProfile;
        this.dtStarted = dtStarted;
        this.dtEnded = dtEnded;
        this.mode = mode;
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
        if (temperatureProfile == null)
            throw new IllegalArgumentException("temperatureProfile is required");
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
