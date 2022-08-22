package com.easleydp.tempctrl.domain;

import static com.easleydp.tempctrl.domain.PropertyUtils.*;

import java.util.Date;

public class Utils {
    /**
     * For time since epoch, save space in json files by using a precision less than
     * 1ms.
     */
    static int reduceUtcMillisPrecision(long ms) {
        return (int) (ms / getReadingsTimestampResolutionMillis());
    }

    // Convenience overload
    static int reduceUtcMillisPrecision(Date date) {
        return (int) (date.getTime() / getReadingsTimestampResolutionMillis());
    }

    // and the reverse
    static long restoreUtcMillisPrecision(int timestamp) {
        return ((long) timestamp) * getReadingsTimestampResolutionMillis();
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

    /** Handy helper, e.g. for XHR handlers */
    public static void artificialDelayForDebugMode() {
        artificialDelayForDebugMode(500);
    }

    public static void artificialDelayForDebugMode(long millis) {
        if (isDebugMode()) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                throw new RuntimeException("Sleep interrupted");
            }
        }
    }
}
