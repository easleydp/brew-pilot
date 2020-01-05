package com.easleydp.tempctrl.domain;

import java.util.Date;

import org.springframework.util.Assert;

import com.easleydp.tempctrl.domain.optimise.Smoother.IntPropertyAccessor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Value object representing a set of readings from a chamber.
 */
public class ChamberReadings
{
    /*
     * NOTE: All temperature values are degrees x 10, e.g. a value of 175 represents 17.5 degrees.
     */

    public ChamberReadings(
            Date timeNow, int tTarget, int tBeer, int tExternal, int tChamber, int tPi,
            int heaterOutput, boolean coolerOn, Mode mode,
            ChamberParameters chamberParameters)
    {
        Assert.isTrue(timeNow != null, "timeNow is required");
        Assert.isTrue(mode != null, "mode is required");

        this.dt = Utils.reduceUtcMillisPrecision(timeNow);

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

    public ChamberReadings() {}  // Default ctor needed for Jackson deserialisation


    private int dt;

    /*
     * Temperatures.
     *
     * These aren't final because they may need to be smoothed.
     *
     * `Integer` only because they may be nulled-out to signify same value as the previous
     * reading. In readings fresh from the chamber they will never be null.
     */

    /**
     * Ordinarily, tTarget simply reflects the target temperature set by this application.
     * But if mode is `HOLD` (or for some reason the ChamberParameters haven't been set)
     * tTarget specifies the beer temperature as it was when the present mode was engaged.
     */
    private Integer tTarget;
    /** Temperature of the beer */
    private Integer tBeer;
    /** Temperature outside the chamber */
    private Integer tExternal;
    /** Temperature inside the chamber */
    private Integer tChamber;
    /** Temperature of the Raspberry Pi */
    private Integer tPi;

    /**
     * Percentage of heater power x 100 (i.e. 0 <= value <= 100), 0 signifying heater off.
     *
     * `Integer` only because it may be nulled-out to signify same value as the previous
     * reading. In readings fresh from the chamber it will never be null.
     */
    private Integer heaterOutput;

    /**
     * Whether the cooler is on or off.
     *
     * `Boolean` only because it may be nulled-out to signify same value as the previous
     * reading. In readings fresh from the chamber it will never be null.
     */
    private Boolean coolerOn;

    /**
     * The state of the chamber manager hardware's "mode" switch.
     *
     * Not `@NonNull` only because it may be nulled-out to signify same value as the previous
     * reading. In readings fresh from the chamber it will never be null.
     */
    private Mode mode;
    public enum Mode
    {
        AUTO,  // Aim for the target temp specified in the ChamberParameters, if any. Otherwise, operate as per `HOLD`.
        HOLD,  // Aim to maintain tBeer as it was when this mode was engaged (reflected in tTarget).
        COOL,  // Force cool (while < tMin). No heating.
        HEAT,  // Force heat (while < tMax). No cooling.
        NONE,  // No heating, no cooling, just monitoring.
    }


    private static final String[] nullablePropertyNames = new String[] {
            "tTarget", "tBeer", "tExternal", "tChamber", "tPi",
            "heaterOutput", "coolerOn", "mode"
    };
    public static String[] getNullablePropertyNames()
    {
        return nullablePropertyNames;
    }


    /**
     * Reflects the ChamberParameters set by this application, or null if none.
     * Enables a sanity check between the controlling application and the chamber manager (Arduino).
     */
    @JsonIgnore
    public ChamberParameters chamberParameters;


    public int getDt()
    {
        return dt;
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer gettTarget()
    {
        return tTarget;
    }
    public void settTarget(Integer tTarget)
    {
        this.tTarget = tTarget;
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer gettBeer()
    {
        return tBeer;
    }
    public void settBeer(Integer tBeer)
    {
        this.tBeer = tBeer;
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer gettExternal()
    {
        return tExternal;
    }
    public void settExternal(Integer tExternal)
    {
        this.tExternal = tExternal;
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer gettChamber()
    {
        return tChamber;
    }
    public void settChamber(Integer tChamber)
    {
        this.tChamber = tChamber;
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer gettPi()
    {
        return tPi;
    }
    public void settPi(Integer tPi)
    {
        this.tPi = tPi;
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getHeaterOutput()
    {
        return heaterOutput;
    }
    public void setHeaterOutput(Integer heaterOutput)
    {
        this.heaterOutput = heaterOutput;
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getCoolerOn()
    {
        return coolerOn;
    }
    public void setCoolerOn(Boolean coolerOn)
    {
        this.coolerOn = coolerOn;
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Mode getMode()
    {
        return mode;
    }
    public void setMode(Mode mode)
    {
        this.mode = mode;
    }


    /* Provide IntPropertyAccessor for each temperature to support smoothing using the Smoother class */

    public static IntPropertyAccessor tTargetAccessor = new IntPropertyAccessor()
    {
        @Override
        public int getValue(Object record)
        {
            return ((ChamberReadings) record).tTarget;
        }
        @Override
        public void setValue(Object record, int value)
        {
            ((ChamberReadings) record).tTarget = value;
        }
    };

    public static IntPropertyAccessor tBeerAccessor = new IntPropertyAccessor()
    {
        @Override
        public int getValue(Object record)
        {
            return ((ChamberReadings) record).tBeer;
        }
        @Override
        public void setValue(Object record, int value)
        {
            ((ChamberReadings) record).tBeer = value;
        }
    };

    public static IntPropertyAccessor tExternalAccessor = new IntPropertyAccessor()
    {
        @Override
        public int getValue(Object record)
        {
            return ((ChamberReadings) record).tExternal;
        }
        @Override
        public void setValue(Object record, int value)
        {
            ((ChamberReadings) record).tExternal = value;
        }
    };
    public static IntPropertyAccessor tChamberAccessor = new IntPropertyAccessor()
    {
        @Override
        public int getValue(Object record)
        {
            return ((ChamberReadings) record).tChamber;
        }
        @Override
        public void setValue(Object record, int value)
        {
            ((ChamberReadings) record).tChamber = value;
        }
    };
    public static IntPropertyAccessor tPiAccessor = new IntPropertyAccessor()
    {
        @Override
        public int getValue(Object record)
        {
            return ((ChamberReadings) record).tPi;
        }
        @Override
        public void setValue(Object record, int value)
        {
            ((ChamberReadings) record).tPi = value;
        }
    };

    public static IntPropertyAccessor[] allTemperatureAccessors = new IntPropertyAccessor[] {
            tTargetAccessor, tBeerAccessor, tExternalAccessor, tChamberAccessor, tPiAccessor};

}
