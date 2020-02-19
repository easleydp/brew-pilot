package com.easleydp.tempctrl.domain;

public class ChamberParameters
{
    /*
     * NOTE: All temperature values are degrees x 10, e.g. a value of 175 represents 17.5 degrees.
     */

    public ChamberParameters(int tTarget, int tTargetNext, int tMin, int tMax, boolean hasHeater, double Kp, double Ki, double Kd, Mode mode)
    {
        this.tTarget = tTarget;
        this.tTargetNext = tTargetNext;
        this.tMin = tMin;
        this.tMax = tMax;
        this.hasHeater = hasHeater;
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
    public final double Kp;
    public final double Ki;
    public final double Kd;
    public final Mode mode;

    @Override
    public String toString()
    {
        return "[tTarget=" + tTarget + ", tTargetNext=" + tTargetNext + ", tMin="
                + tMin + ", tMax=" + tMax + ", hasHeater=" + hasHeater + ", Kp=" + Kp + ", Ki=" + Ki
                + ", Kd=" + Kd + ", mode=" + mode + "]";
    }
}
