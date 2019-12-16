package com.easleydp.tempctrl.spring;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.easleydp.tempctrl.domain.Chamber;
import com.easleydp.tempctrl.domain.ChamberManager;
import com.easleydp.tempctrl.domain.ChamberRepository;

/**
 * Note: Scheduling of taking readings is separated from the actual collecting of readings for the sake of testability
 * (collecting of readings being something we want to unit test, without Spring).
 */
@Component
public class CollectReadingsScheduler
{
    private static final Logger logger = LoggerFactory.getLogger(CollectReadingsScheduler.class);

    @Autowired
    private ChamberRepository chamberRepository;

    @Autowired
    private ChamberManager chamberManager;

    private long longestDuration = 100;

    @Scheduled(fixedRateString="${readings.periodMillis}")
    public void takeReadings()
    {
        logger.debug("takeReadings called");

        Date date = new Date();
        // For each chamber, if it has an active gyle, collect a set of readings.
        chamberRepository.getChambers().stream()
            .map(Chamber::getActiveGyle)
            .filter(ag -> ag != null)
            .forEach(ag -> {
                logger.debug("taking readings for chamber " + ag.chamber.getId() + " gyle " + ag.gyleDir.getFileName());
                ag.collectReadings(chamberManager, date);
            });

        long duration = System.currentTimeMillis() - date.getTime();
        if (duration > longestDuration)
        {
            logger.warn("takeReadings took " + duration + "ms");
            longestDuration = duration;
        }
        else if (logger.isDebugEnabled())
        {
            logger.debug("takeReadings took " + duration + "ms");
        }
    }
}
