package com.easleydp.tempctrl.domain;

import java.util.Date;

import org.springframework.util.Assert;

import com.easleydp.tempctrl.domain.optimise.Smoother.IntPropertyAccessor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Value object representing a serialised readings record (set of readings from
 * a chamber).
 */
public class ChamberReadings {
    /*
     * NOTE: All temperature values are degrees x 10, e.g. a value of 175 represents
     * 17.5 degrees.
     */

    // Default ctor needed for Jackson deserialisation
    public ChamberReadings() {
    }

    public ChamberReadings(Date timeNow, int tTarget, int tBeer, int tExternal, int tChamber, int tPi,
            Integer heaterOutput, boolean fridgeOn, Mode mode) {
        Assert.isTrue(timeNow != null, "timeNow is required");

        this.dt = Utils.reduceUtcMillisPrecision(timeNow);

        this.tTarget = tTarget;
        this.tBeer = tBeer;
        this.tExternal = tExternal;
        this.tChamber = tChamber;
        this.tPi = tPi;

        this.heaterOutput = heaterOutput;
        this.fridgeOn = fridgeOn;
        this.mode = mode;
    }

    public ChamberReadings(ChamberReadings cr) {
        this.dt = cr.dt;
        this.tTarget = cr.tTarget;
        this.tBeer = cr.tBeer;
        this.tExternal = cr.tExternal;
        this.tChamber = cr.tChamber;
        this.tPi = cr.tPi;
        this.heaterOutput = cr.heaterOutput;
        this.fridgeOn = cr.fridgeOn;
        this.mode = cr.mode;
    }

    private int dt;

    /*
     * Temperatures.
     *
     * These aren't final because they may need to be smoothed.
     *
     * `Integer` only because they may be nulled-out to signify same value as the
     * previous reading. In readings fresh from the chamber they will never be null.
     */

    /**
     * Ordinarily, tTarget simply reflects the target temperature set by this
     * application. But if mode is `HOLD`, tTarget specifies the beer temperature as
     * it was when the present mode was engaged.
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
     * Percentage of heater power x 100 (i.e. 0 <= value <= 100), 0 signifying
     * heater off.
     *
     * `Integer` only because it may be nulled-out to signify same value as the
     * previous reading, or when no heater. In readings fresh from a chamber
     * equipped with a heater it will never be null.
     */
    private Integer heaterOutput;

    /**
     * Whether the cooler is on or off.
     *
     * `Boolean` only because it may be nulled-out to signify same value as the
     * previous reading. In readings fresh from the chamber it will never be null.
     */
    private Boolean fridgeOn;

    /**
     * Current mode for the chamber / active gyle.
     *
     * `null` signifies same value as the previous reading. In readings fresh from
     * the chamber it will never be null.
     */
    private Mode mode;

    private static final String[] nullablePropertyNames = new String[] { "tTarget", "tBeer", "tExternal", "tChamber",
            "tPi", "heaterOutput", "fridgeOn", "mode" };

    public static String[] getNullablePropertyNames() {
        return nullablePropertyNames;
    }

    @Override
    public String toString() {
        return "[dt=" + dt + ", tTarget=" + tTarget + ", tBeer=" + tBeer + ", tExternal=" + tExternal + ", tChamber="
                + tChamber + ", tPi=" + tPi + ", heaterOutput=" + heaterOutput + ", fridgeOn=" + fridgeOn + ", mode="
                + mode + "]";
    }

    public int getDt() {
        return dt;
    }

    public void setDt(int dt) {
        this.dt = dt;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer gettTarget() {
        return tTarget;
    }

    public void settTarget(Integer tTarget) {
        this.tTarget = tTarget;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer gettBeer() {
        return tBeer;
    }

    public void settBeer(Integer tBeer) {
        this.tBeer = tBeer;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer gettExternal() {
        return tExternal;
    }

    public void settExternal(Integer tExternal) {
        this.tExternal = tExternal;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer gettChamber() {
        return tChamber;
    }

    public void settChamber(Integer tChamber) {
        this.tChamber = tChamber;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer gettPi() {
        return tPi;
    }

    public void settPi(Integer tPi) {
        this.tPi = tPi;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL) // Note: we deliberately include zero since non-inclusion generally
                                               // signifies 'same as previous value'.
    public Integer getHeaterOutput() {
        return heaterOutput;
    }

    public void setHeaterOutput(Integer heaterOutput) {
        this.heaterOutput = heaterOutput;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL) // Note: we deliberately include false since non-inclusion generally
                                               // signifies 'same as previous value'.
    public Boolean getFridgeOn() {
        return fridgeOn;
    }

    public void setFridgeOn(Boolean fridgeOn) {
        this.fridgeOn = fridgeOn;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /**
     * Convenience accessor. Distinguishes heater being ON to actually raise the
     * beer temperature rather than just for 'maintenance heating'.
     * 
     * @return whether heater is ON AND the beer is COLD
     */
    @JsonIgnore
    public boolean isTrulyHeating() {
        return heaterOutput > 0 && tBeer < tTarget;
    }

    /*
     * Provide IntPropertyAccessor for each temperature to support smoothing using
     * the Smoother class
     */

    public static IntPropertyAccessor tTargetAccessor = new IntPropertyAccessor() {
        @Override
        public int getValue(Object record) {
            return ((ChamberReadings) record).tTarget;
        }

        @Override
        public void setValue(Object record, int value) {
            ((ChamberReadings) record).tTarget = value;
        }
    };

    public static IntPropertyAccessor tBeerAccessor = new IntPropertyAccessor() {
        @Override
        public int getValue(Object record) {
            return ((ChamberReadings) record).tBeer;
        }

        @Override
        public void setValue(Object record, int value) {
            ((ChamberReadings) record).tBeer = value;
        }
    };

    public static IntPropertyAccessor tExternalAccessor = new IntPropertyAccessor() {
        @Override
        public int getValue(Object record) {
            return ((ChamberReadings) record).tExternal;
        }

        @Override
        public void setValue(Object record, int value) {
            ((ChamberReadings) record).tExternal = value;
        }
    };
    public static IntPropertyAccessor tChamberAccessor = new IntPropertyAccessor() {
        @Override
        public int getValue(Object record) {
            return ((ChamberReadings) record).tChamber;
        }

        @Override
        public void setValue(Object record, int value) {
            ((ChamberReadings) record).tChamber = value;
        }
    };
    public static IntPropertyAccessor tPiAccessor = new IntPropertyAccessor() {
        @Override
        public int getValue(Object record) {
            return ((ChamberReadings) record).tPi;
        }

        @Override
        public void setValue(Object record, int value) {
            ((ChamberReadings) record).tPi = value;
        }
    };

    public static IntPropertyAccessor[] allTemperatureAccessors = new IntPropertyAccessor[] { tTargetAccessor,
            tBeerAccessor, tExternalAccessor, tChamberAccessor, tPiAccessor };

}
