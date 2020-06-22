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

    public GyleDto() {}

    public GyleDto(String name, TemperatureProfileDto temperatureProfile, Long dtStarted, Long dtEnded) {
        this.name = name;
        this.temperatureProfile = temperatureProfile;
        this.dtStarted = dtStarted;
        this.dtEnded = dtEnded;
    }

    public String getName()
    {
        return name;
    }
    public void setName(String name)
    {
        this.name = name;
    }
    public TemperatureProfileDto getTemperatureProfile()
    {
        return temperatureProfile;
    }
    public void setTemperatureProfile(TemperatureProfileDto temperatureProfile)
    {
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

    @Override
    public String toString() {
        return "{" +
            " name='" + getName() + "'" +
            ", temperatureProfile=" + getTemperatureProfile() +
            ", dtStarted=" + getDtStarted() +
            ", dtEnded=" + getDtEnded() +
            "}";
    }
}
