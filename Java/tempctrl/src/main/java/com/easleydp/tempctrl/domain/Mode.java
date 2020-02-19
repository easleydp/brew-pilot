package com.easleydp.tempctrl.domain;

import java.util.HashMap;
import java.util.Map;

import org.springframework.util.Assert;

public enum Mode
{
    // Each mode has a single character code (convenient for the microcontroller).
    AUTO('A'),  // Aim for the target temp specified in the ChamberParameters, if any. Otherwise, operate as per `HOLD`.
    HOLD('O'),  // Aim to maintain tBeer as it was when this mode was engaged (reflected in tTarget).
    COOL('C'),  // Force cool (while < tMin). No heating.
    HEAT('H'),  // Force heat (while < tMax). No cooling.
    NONE('N');  // No heating, no cooling, just monitoring.

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

    public char getCode() {
        return code;
    }

    public static Mode get(char code) {
        Mode mode = lookup.get(code);
        if (mode == null)
            throw new IllegalArgumentException("Illegal Mode code: " + code);
        return mode;
    }
    // Convenience overload, with error checking for string length.
    // Also handles the special value "-", which (when from the Arduino) signifies none.
    public static Mode get(String code)
    {
        Assert.state(code.length() == 1, "Mode code should be a single character: [" + code + "]");
        char ch = code.charAt(0);
        return ch == '-' ? null : get(ch);
    }
}
