package com.easleydp.tempctrl.spring;

import java.io.IOException;
import java.util.Date;
import java.util.IntSummaryStatistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.easleydp.tempctrl.domain.Chamber;
import com.easleydp.tempctrl.domain.ChamberManager;
import com.easleydp.tempctrl.domain.ChamberParameters;
import com.easleydp.tempctrl.domain.ChamberReadings;
import com.easleydp.tempctrl.domain.ChamberRepository;
import com.easleydp.tempctrl.domain.Gyle;

/**
 * Note: Scheduling of taking readings is separated from the actual collecting
 * of readings for the sake of testability (collecting of readings being
 * something we want to unit test, without Spring).
 */
@Component
public class CollectReadingsScheduler {
    private static final Logger logger = LoggerFactory.getLogger(CollectReadingsScheduler.class);

    @Autowired
    private ChamberRepository chamberRepository;

    @Autowired
    private ChamberManager chamberManager;

    private IntSummaryStatistics durationStats = new IntSummaryStatistics();
    private boolean first = true;

    @Scheduled(fixedRateString = "${readings.periodMillis}")
    public void collectReadings() {
        logger.debug("collectReadings called");

        Date timeNow = new Date();
        // @formatter:off
        chamberRepository.getChambers()
            .forEach(ch -> {
                ch.checkForUpdates();
                collectReadingsForChamber(ch, timeNow);
            });
        // @formatter:on

        logDuration((int) (System.currentTimeMillis() - timeNow.getTime()));
    }

    /**
     * For the supplied chamber: send chamber params and, if it has an active gyle,
     * collect a set of readings.
     */
    private void collectReadingsForChamber(Chamber ch, Date timeNow) {
        final int chamberId = ch.getId();
        Gyle lg = ch.getLatestGyle();

        try {
            logger.debug("================ collectReadingsForChamber(" + chamberId + ", " + timeNow + ")");
            logger.debug("Slurping log messages BEFORE SENDING parameters to chamber " + chamberId);
            chamberManager.slurpLogMessages();
            ChamberParameters cp = lg != null ? lg.getChamberParameters(timeNow) : ch.getPartialChamberParameters();
            chamberManager.setParameters(chamberId, cp);
            logger.debug("Slurping log messages AFTER SENDING parameters to chamber " + chamberId);
            chamberManager.slurpLogMessages();

            logger.debug("Taking readings for chamber " + lg.chamber.getId());
            ChamberReadings latestReadings = chamberManager.collectReadings(chamberId, timeNow);
            if (logger.isDebugEnabled()) {
                logger.debug("Chamber " + chamberId + " readings: " + latestReadings.toString());
            }
            ch.setLatestChamberReadings(latestReadings);
            logger.debug("Slurping log messages AFTER COLLECTING readings for chamber " + chamberId);
            chamberManager.slurpLogMessages();

            if (lg != null && lg.isActive()) {
                lg.logLatestReadings(latestReadings, timeNow);
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
            if (t instanceof IOException)
                chamberManager.handleIOException((IOException) t);
        }
    }

    private void logDuration(int duration) {
        logger.debug("collectReadings took {}ms", duration);

        // First collection tends to be so slow as to significantly skew the stats, so
        // ignore.
        if (first) {
            first = false;
        } else {
            // Log warning if duration is significantly longer than average.
            int average = (int) (durationStats.getAverage() + 0.5);
            if (durationStats.getCount() > 10 && duration >= average * 2) {
                logger.warn("collectReadings took {}ms compared to average of {}ms and max of {}ms.", duration, average,
                        durationStats.getMax());
            }
            durationStats.accept(duration);
        }
    }

    // Summary stats for external parties
    public static class ReadingsCollectionDurationStats {
        public final int min, max, average;

        ReadingsCollectionDurationStats(int min, int max, int average) {
            this.min = min;
            this.max = max;
            this.average = average;
        }
    }

    public ReadingsCollectionDurationStats getReadingsCollectionDurationStats() {
        if (durationStats.getCount() == 0) {
            return null;
        }
        int average = (int) (durationStats.getAverage() + 0.5);
        return new ReadingsCollectionDurationStats(durationStats.getMin(), durationStats.getMax(), average);
    }

}
