package com.easleydp.tempctrl.domain;

import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

public class ChamberReadings
{
    /*
     * NOTE: All temperature values are degrees x 10, e.g. a value of 175 represents 17.5 degrees.
     */

    public ChamberReadings(
            int tTarget, int tBeer, int tExternal, int tChamber, int tPi,
            Integer heaterOutput, boolean coolerOn, Mode mode,
            ChamberParameters chamberParameters)
    {
        Assert.isTrue(mode != null, "mode is required");

        this.tTarget = tTarget;
        this.tBeer = tBeer;
        this.tExternal = tExternal;
        this.tChamber = tChamber;
        this.tPi = tPi;

        this.heaterOutput = heaterOutput;
        this.coolerOn = coolerOn;
        this.mode = mode;
        this.chamberParameters = chamberParameters;
    }

    /**
     * Ordinarily, tTarget simply reflects the target temperature set by this application.
     * But if mode is `HOLD` (or for some reason the ChamberParameters haven't been set)
     * tTarget specifies the beer temperature as it was when the present mode was engaged.
     */
    public final int tTarget;
    /** Temperature of the beer */
    public final int tBeer;
    /** Temperature outside the chamber */
    public final int tExternal;
    /** Temperature inside the chamber */
    public final int tChamber;
    /** Temperature of the Raspberry Pi */
    public final int tPi;

    /** Percentage of heater power x 100 (i.e. 0 < value <= 100), or null if the heater is off. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public final Integer heaterOutput;

    /** Whether the cooler is on or off. */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public final boolean coolerOn;

    /**
     * The state of the chamber manager hardware's "mode" switch.
     */
    @NonNull
    public final Mode mode;
    public enum Mode
    {
        AUTO,  // Aim for the target temp specified in the ChamberParameters, if any. Otherwise, operate as per `HOLD`.
        HOLD,  // Aim to maintain tBeer as it was when this mode was engaged (reflected in tTarget).
        COOL,  // Force cool (while < tMin). No heating.
        HEAT,  // Force heat (while < tMax). No cooling.
        NONE,  // No heating, no cooling, just monitoring.
    }

    /**
     * Reflects the ChamberParameters set by this application, or null if none.
     * Enables a sanity check between the controlling application and the chamber manager (Arduino).
     */
    @JsonIgnore
    public final ChamberParameters chamberParameters;
}
