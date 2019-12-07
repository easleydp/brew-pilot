package com.easleydp.tempctrl.domain;

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
    public void setParameters(int chamberId, ChamberParameters params);

    public ChamberReadings getReadings(int chamberId, Date timeNow);
}
