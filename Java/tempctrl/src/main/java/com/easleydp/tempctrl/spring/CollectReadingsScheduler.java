package com.easleydp.tempctrl.spring;

import java.io.IOException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.easleydp.tempctrl.domain.Chamber;
import com.easleydp.tempctrl.domain.ChamberManager;
import com.easleydp.tempctrl.domain.ChamberParameters;
import com.easleydp.tempctrl.domain.ChamberRepository;
import com.easleydp.tempctrl.domain.Gyle;

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
    private boolean first = true;

    @Scheduled(fixedRateString="${readings.periodMillis}")
    public void takeReadings()
    {
        logger.debug("takeReadings called");

        if (first)
        {
            //sleep(5000);
            first = false;
        }

        Date date = new Date();
        chamberRepository.getChambers().stream()
            .forEach(ch -> {
                ch.checkForGyleUpdates();
                takeReadingsForChamber(ch, date);
            });

        long duration = System.currentTimeMillis() - date.getTime();
        if (duration > longestDuration)
        {
            logger.warn("takeReadings took " + duration + "ms (longest yet)");
            longestDuration = duration;
        }
        else if (logger.isDebugEnabled())
        {
            logger.debug("takeReadings took " + duration + "ms");
        }
    }

    /** For the supplied chamber: send chamber params and, if it has an active gyle, collect a set of readings. */
    private void takeReadingsForChamber(Chamber ch, Date date)
    {
        final int chamberId = ch.getId();
        Gyle lg = ch.getLatestGyle();

        try
        {
        	logger.debug("================ takeReadingsForChamber(" + chamberId + ", " + date + ")");
            logger.debug("Slurping log messages BEFORE SENDING parameters to chamber " + chamberId);
            chamberManager.slurpLogMessages();
            ChamberParameters cp = lg != null ? lg.getChamberParameters(date) : ch.getPartialChamberParameters();
            chamberManager.setParameters(chamberId, cp);
            logger.debug("Slurping log messages AFTER SENDING parameters to chamber " + chamberId);
            chamberManager.slurpLogMessages();

            if (lg != null  &&  lg.isActive())
            {
                logger.debug("Taking readings for chamber " + lg.chamber.getId() + " gyle " + lg.gyleDir.getFileName());
                lg.collectReadings(chamberManager, date);
                logger.debug("Slurping log messages AFTER COLLECTING readings for chamber " + chamberId);
                chamberManager.slurpLogMessages();
            }
        }
        catch (Throwable t)
        {
            logger.error(t.getMessage(), t);
            if (t instanceof IOException)
                chamberManager.handleIOException((IOException) t);
        }
    }

}
