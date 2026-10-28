package com.easleydp.tempctrl.domain;

import java.util.Date;

public class Utils {
    /**
     * For time since epoch, save space in json files by using a precision less than
     * 1ms.
     */
    static int reduceUtcMillisPrecision(long ms, int timestampResolutionMillis) {
        return (int) (ms / timestampResolutionMillis);
    }

    // Convenience overload
    static int reduceUtcMillisPrecision(Date date, int timestampResolutionMillis) {
        return reduceUtcMillisPrecision(date.getTime(), timestampResolutionMillis);
    }

    // and the reverse
    static long restoreUtcMillisPrecision(int timestamp, int timestampResolutionMillis) {
        return ((long) timestamp) * timestampResolutionMillis;
    }

    /**
     * When sending notifications such as "time to bottle" it's more humane to only
     * specify time to nearest hour.
     */
    public static Date roundToNearestHour(Date d) {
        long oneHourMs = 1000L * 60 * 60;
        long ms = d.getTime() + oneHourMs / 2; // add half an hour before we round down
        return new Date((ms / oneHourMs) * oneHourMs);
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException("Sleep interrupted");
        }
    }

    /** Handy helper, e.g. for XHR handlers */
    public static void artificialDelayForDebugMode(boolean debug) {
        artificialDelayForDebugMode(500, debug);
    }

    public static void artificialDelayForDebugMode(long millis, boolean debug) {
        if (debug) {
            sleep(millis);
        }
    }
}
