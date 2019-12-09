package com.easleydp.tempctrl.spring;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.easleydp.tempctrl.domain.Chamber;
import com.easleydp.tempctrl.domain.ChamberManager;
import com.easleydp.tempctrl.domain.Chambers;

/**
 * Note: Scheduling of taking readings is separated from the actual collecting of readings for the sake of testability
 * (collecting of readings being something we want to unit test, without Spring).
 */
@Component
public class CollectReadingsScheduler
{
    @Autowired
    private Chambers chambers;

    @Autowired
    private ChamberManager chamberManager;

    @Scheduled(fixedRateString="${readings.periodMillis}")
    public void takeReadings()
    {
        Date nowTime = new Date();
        // For each chamber, if it has an active gyle, collect its readings.
        chambers.getChambers().stream()
            .map(Chamber::getActiveGyle)
            .filter(ag -> ag != null)
            .forEach(ag -> ag.collectReadings(chamberManager, nowTime));
    }
}
