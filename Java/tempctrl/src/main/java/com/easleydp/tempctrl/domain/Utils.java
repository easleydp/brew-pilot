package com.easleydp.tempctrl.domain;

import java.util.Date;

public class Utils
{
    /** For time since epoch, save space in json files by using 30 second rather than 1 ms precision. */
    static int reduceUtcMillisPrecision(long ms)
    {
        return (int) (ms / (1000 * 30));
    }
    // Convenience overload
    static int reduceUtcMillisPrecision(Date date)
    {
        return (int) (date.getTime() / (1000 * 30));
    }

    static long restoreUtcMillisPrecision(int thirtySecs)
    {
        return (thirtySecs) * 1000L * 30L;
    }

}
