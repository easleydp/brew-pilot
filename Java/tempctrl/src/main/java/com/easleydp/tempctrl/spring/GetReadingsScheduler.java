package com.easleydp.tempctrl.spring;

import java.util.Collection;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.easleydp.tempctrl.domain.Chamber;
import com.easleydp.tempctrl.domain.ChamberManager;

/**
 * Note: Scheduling of taking readings is separated from the actual collecting of readings for the sake of testability
 * (collecting of readings being something we want to unit test, without Spring).
 */
@Component
public class GetReadingsScheduler
{
    @Autowired
    private Collection<Chamber> chambers;

    @Autowired
    private ChamberManager chamberManager;

    @Scheduled(fixedRate = 1000 * 60)  // Every minute. TODO: Make this configurable.
    public void takeReadings()
    {
        Date nowTime = new Date();
        // For each chamber, if it has an active gyle, collect its readings.
        chambers.stream()
            .map(Chamber::getActiveGyle)
            .filter(ag -> ag != null)
            .forEach(ag -> ag.collectReadings(chamberManager, nowTime));
    }
}
