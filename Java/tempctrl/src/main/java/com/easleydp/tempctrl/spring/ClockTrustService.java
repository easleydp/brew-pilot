package com.easleydp.tempctrl.spring;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.easleydp.tempctrl.util.OsCommandExecuter;

@Component
public class ClockTrustService {
    private static final Logger logger = LoggerFactory.getLogger(ClockTrustService.class);
    private static final String[] CLOCK_SYNC_COMMAND = {
            "timedatectl", "show", "--property=NTPSynchronized", "--value"
    };

    private volatile boolean clockTrusted;
    private Boolean previousClockTrusted;

    @PostConstruct
    public void checkOnStartup() {
        checkClockTrust();
    }

    @Scheduled(fixedDelayString = "${app.clock-trust.check-period-millis:180000}")
    public void checkClockTrust() {
        updateClockTrust(OsCommandExecuter.executeWithNoExitCodeError(CLOCK_SYNC_COMMAND));
    }

    public boolean isClockTrusted() {
        return clockTrusted;
    }

    synchronized void updateClockTrust(String commandOutput) {
        boolean trusted = "yes".equals(commandOutput);
        if (previousClockTrusted == null || previousClockTrusted != trusted) {
            if (trusted) {
                logger.info("System clock is synchronised.");
            } else {
                logger.warn("System clock is not synchronised; chamber control and readings collection are paused.");
            }
            previousClockTrusted = trusted;
        }
        clockTrusted = trusted;
    }
}
