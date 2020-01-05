package com.easleydp.tempctrl.domain;

import static com.easleydp.tempctrl.domain.PropertyUtils.*;

import java.util.Date;

public class Utils
{
    /** For time since epoch, save space in json files by using a precision less than 1ms. */
    static int reduceUtcMillisPrecision(long ms)
    {
        return (int) (ms / getReadingsTimestampResolutionMillis());
    }
    // Convenience overload
    static int reduceUtcMillisPrecision(Date date)
    {
        return (int) (date.getTime() / getReadingsTimestampResolutionMillis());
    }

    static long restoreUtcMillisPrecision(int timestamp)
    {
        return ((long) timestamp) * getReadingsTimestampResolutionMillis();
    }
}
