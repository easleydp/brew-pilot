package com.easleydp.tempctrl.domain;

/**
 * A _chamber_ is a temperature controlled container in which beer
 * fermentation, maturation or storage takes place (usually within one or more
 * smaller vessels, e.g. buckets or bottles).
 * 
 * Each chamber is assumed to have refrigeration but a heater is optional. A
 * chamber without a heater is assumed to be a _beer fridge_.
 * 
 * Each chamber may have any number of associated _gyles_ (all but the latest
 * being historical). For a given chamber, the latest gyle is considered
 * _active_ if it has a start time earlier than now AND either no end time or an
 * end time later than now. A beer fridge typically has only one gyle, with a
 * flat temperature profile, and always active (no end time).
 * 
 * 
 * The data attributes marry-up with the the contents of chamber.json
 */
public class ChamberDto {
    private String name;
    private int heaterPowerWatts;
    private int tMin;
    private int tMax;
    private boolean hasHeater;
    private int fridgeMinOnTimeMins;
    private int fridgeMinOffTimeMins;
    private int fridgeSwitchOnLagMins;
    private double kp;
    private double ki;
    private double kd;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getFridgeMinOnTimeMins() {
        return fridgeMinOnTimeMins;
    }

    public void setFridgeMinOnTimeMins(int fridgeMinOnTimeMins) {
        this.fridgeMinOnTimeMins = fridgeMinOnTimeMins;
    }

    public int getFridgeMinOffTimeMins() {
        return fridgeMinOffTimeMins;
    }

    public void setFridgeMinOffTimeMins(int fridgeMinOffTimeMins) {
        this.fridgeMinOffTimeMins = fridgeMinOffTimeMins;
    }

    public int getFridgeSwitchOnLagMins() {
        return fridgeSwitchOnLagMins;
    }

    public void setFridgeSwitchOnLagMins(int fridgeSwitchOnLagMins) {
        this.fridgeSwitchOnLagMins = fridgeSwitchOnLagMins;
    }

    public int getHeaterPowerWatts() {
        return heaterPowerWatts;
    }

    public void setHeaterPowerWatts(int heaterPowerWatts) {
        this.heaterPowerWatts = heaterPowerWatts;
    }

    public int gettMin() {
        return tMin;
    }

    public void settMin(int tMin) {
        this.tMin = tMin;
    }

    public int gettMax() {
        return tMax;
    }

    public void settMax(int tMax) {
        this.tMax = tMax;
    }

    public boolean isHasHeater() {
        return hasHeater;
    }

    public void setHasHeater(boolean hasHeater) {
        this.hasHeater = hasHeater;
    }

    public double getKp() {
        return kp;
    }

    public void setKp(double kp) {
        this.kp = kp;
    }

    public double getKi() {
        return ki;
    }

    public void setKi(double ki) {
        this.ki = ki;
    }

    public double getKd() {
        return kd;
    }

    public void setKd(double kd) {
        this.kd = kd;
    }

}
