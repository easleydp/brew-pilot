package com.easleydp.tempctrl.domain;

public class ChamberParameters
{
    /*
     * NOTE: All temperature values are degrees x 10, e.g. a value of 175 represents 17.5 degrees.
     */

    public ChamberParameters(int tTarget, int tTargetNext, int tMin, int tMax)
    {
        this.tTarget = tTarget;
        this.tTargetNext = tTargetNext;
        this.tMin = tMin;
        this.tMax = tMax;
    }

    public final int tTarget;
    public final int tTargetNext;
    public final int tMin;
    public final int tMax;
}
