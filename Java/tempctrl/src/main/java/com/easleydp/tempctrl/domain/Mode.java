package com.easleydp.tempctrl.domain;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import org.springframework.util.Assert;

public enum Mode {
    /*
     * Each mode has a single character code (convenient for the microcontroller).
     */

    // Aim for the target temp specified in the ChamberParameters.
    AUTO('A'),
    // Aim to maintain tBeer as it was when this mode was engaged.
    HOLD('H'),
    // As AUTO but disable heater.
    DISABLE_HEATER('*'),
    // As AUTO but disable fridge.
    DISABLE_FRIDGE('~'),
    // No heating, no cooling, just monitoring.
    MONITOR_ONLY('M');

    private final char code;

    // Reverse-lookup map for getting a Mode from a code
    private static final Map<Character, Mode> lookup = new HashMap<>();

    static {
        for (Mode m : Mode.values()) {
            if (lookup.containsKey(m.getCode()))
                throw new RuntimeException("Duplicate code: " + m.getCode());
            lookup.put(m.getCode(), m);
        }
    }

    private Mode(char code) {
        this.code = code;
    }

    @JsonValue
    public char getCode() {
        return code;
    }

    @JsonCreator
    public static Mode get(char code) {
        Mode mode = lookup.get(code);
        if (mode == null)
            throw new IllegalArgumentException("Illegal Mode code: " + code);
        return mode;
    }

    // Convenience overload, with error checking for string length.
    public static Mode get(String code) {
        Assert.state(code.length() == 1, "Mode code should be a single character: [" + code + "]");
        return get(code.charAt(0));
    }

    @Override
    public String toString() {
        return "" + getCode();
    }
}
