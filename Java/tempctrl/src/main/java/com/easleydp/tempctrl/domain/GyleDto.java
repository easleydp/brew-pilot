package com.easleydp.tempctrl.domain;

/**
 * Marries-up with the the contents of gyle.json
 */
public class GyleDto
{
    private String name;
    private TemperatureProfileDto temperatureProfile;
    private Long dtStarted;  // Null is not started
    private Long dtEnded;  // Null is not ended

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
}
