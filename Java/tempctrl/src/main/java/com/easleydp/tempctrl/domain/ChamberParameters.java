package com.easleydp.tempctrl.domain;

public class ChamberParameters
{
    /*
     * NOTE: All temperature values are degrees x 10, e.g. a value of 175 represents 17.5 degrees.
     */

    public ChamberParameters(int tTarget, int tTargetNext, int tMin, int tMax, boolean hasHeater,
        int fridgeMinOnTimeMins, int fridgeMinOffTimeMins, int fridgeSwitchOnLagMins,
        double Kp, double Ki, double Kd, Mode mode)
    {
        this.tTarget = tTarget;
        this.tTargetNext = tTargetNext;
        this.tMin = tMin;
        this.tMax = tMax;
        this.hasHeater = hasHeater;
        this.fridgeMinOnTimeMins = fridgeMinOnTimeMins;
        this.fridgeMinOffTimeMins = fridgeMinOffTimeMins;
        this.fridgeSwitchOnLagMins = fridgeSwitchOnLagMins;
        this.Kp = Kp;
        this.Ki = Ki;
        this.Kd = Kd;
        this.mode = mode;
    }

    public final int tTarget;
    /** tTargetNext reflects what tTarget WILL be one hour from 'now'. */
    public final int tTargetNext;
    public final int tMin;
    public final int tMax;
    public final boolean hasHeater;
    public final int fridgeMinOnTimeMins;
    public final int fridgeMinOffTimeMins;
    public final int fridgeSwitchOnLagMins;
    public final double Kp;
    public final double Ki;
    public final double Kd;
    public final Mode mode;

    @Override
    public String toString()
    {
        return "[tTarget=" + tTarget + ", tTargetNext=" + tTargetNext
                + ", tMin=" + tMin + ", tMax=" + tMax + ", hasHeater=" + hasHeater
                + ", fridgeMinOnTimeMins=" + fridgeMinOnTimeMins + ", fridgeMinOffTimeMins=" + fridgeMinOffTimeMins + ", fridgeSwitchOnLagMins=" + fridgeSwitchOnLagMins
                + ", Kp=" + Kp + ", Ki=" + Ki + ", Kd=" + Kd + ", mode=" + mode + "]";
    }
}
