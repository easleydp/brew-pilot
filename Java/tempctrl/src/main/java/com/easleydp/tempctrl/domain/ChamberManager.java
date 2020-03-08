package com.easleydp.tempctrl.domain;

import java.io.IOException;
import java.util.Date;

/**
 * Interface to the standalone chamber manager hardware device (e.g. Arduino).
 *
 * There is only a single such device in a tempctrl system. One device can manage multiple
 * chambers, e.g. a beer fridge and a fermentation chamber. The device identifies these
 * ordinally with an integer ID starting from 1.
 */
public interface ChamberManager
{
    void setParameters(int chamberId, ChamberParameters params) throws IOException;

    ChamberReadings getReadings(int chamberId, Date timeNow) throws IOException;

    default ChamberManagerStatus getChamberManagerStatus() throws IOException
    {
        return new ChamberManagerStatus(-1, -1, false, false);
    }

    void slurpLogMessages() throws IOException;

    /**
     * Perform any remedial action that may be necessary on one of the above method
     * throwing an IOException, e.g. reset comms port.
     */
    default void handleIOException(IOException e) {}
}
