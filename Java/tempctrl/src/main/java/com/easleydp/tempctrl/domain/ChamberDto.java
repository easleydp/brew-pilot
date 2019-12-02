package com.easleydp.tempctrl.domain;

/**
 * Marries-up with the the contents of chamber.json
 */
public class ChamberDto
{
    private String name;
    private int fridgeMinOnTimeMins;
    private int fridgeMinOffTimeMins;
    private int heaterPowerWatts;

    public String getName()
    {
        return name;
    }
    public void setName(String name)
    {
        this.name = name;
    }
    public int getFridgeMinOnTimeMins()
    {
        return fridgeMinOnTimeMins;
    }
    public void setFridgeMinOnTimeMins(int fridgeMinOnTimeMins)
    {
        this.fridgeMinOnTimeMins = fridgeMinOnTimeMins;
    }
    public int getFridgeMinOffTimeMins()
    {
        return fridgeMinOffTimeMins;
    }
    public void setFridgeMinOffTimeMins(int fridgeMinOffTimeMins)
    {
        this.fridgeMinOffTimeMins = fridgeMinOffTimeMins;
    }
    public int getHeaterPowerWatts()
    {
        return heaterPowerWatts;
    }
    public void setHeaterPowerWatts(int heaterPowerWatts)
    {
        this.heaterPowerWatts = heaterPowerWatts;
    }

}
