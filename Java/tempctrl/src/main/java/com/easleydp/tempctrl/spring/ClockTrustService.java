package com.easleydp.tempctrl.spring;

import java.time.Instant;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.easleydp.tempctrl.util.OsCommandExecuter;

@Component
public class ClockTrustService {
    private static final Logger logger = LoggerFactory.getLogger(ClockTrustService.class);
    private static final String[] CLOCK_SYNC_COMMAND = {
            "timedatectl", "show", "--property=NTPSynchronized", "--value"
    };
    private static final long MIN_PLAUSIBLE_CLOCK_EPOCH_MILLIS = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli();

    private final boolean rtcEnabled;
    // `clockTrusted` can flip between true and false as NTP access comes and goes,
    // whereas `controlTimeEstablished` latches true (the hardware clock continues
    // keeping time).
    private volatile boolean clockTrusted;
    private volatile boolean controlTimeEstablished;

    private Boolean previousClockTrusted;

    public ClockTrustService(@Value("${app.clock-trust.rtc-enabled:false}") boolean rtcEnabled) {
        this.rtcEnabled = rtcEnabled;
    }

    @PostConstruct
    public void checkOnStartup() {
        updateClockTrust(true, System.currentTimeMillis());
    }

    @Scheduled(fixedDelayString = "${app.clock-trust.check-period-millis:180000}")
    public void checkClockTrust() {
        updateClockTrust(false, System.currentTimeMillis());
    }

    public boolean isClockTrusted() {
        return clockTrusted;
    }

    public boolean isControlTimeEstablished() {
        return controlTimeEstablished;
    }

    private void updateClockTrust(boolean startupCheck, long currentTimeMillis) {
        updateClockTrust(isNtpSynchronized(), startupCheck, currentTimeMillis);
    }

    private boolean isNtpSynchronized() {
        return "yes".equals(OsCommandExecuter.executeWithNoExitCodeError(CLOCK_SYNC_COMMAND));
    }

    synchronized void updateClockTrust(boolean ntpSynchronized, boolean startupCheck, long currentTimeMillis) {
        boolean trusted = ntpSynchronized;
        boolean plausibleRtcTime = !trusted && startupCheck && rtcEnabled
                && isPlausibleClockTime(currentTimeMillis);
        clockTrusted = trusted;
        if (trusted) {
            controlTimeEstablished = true;
        } else if (plausibleRtcTime) {
            controlTimeEstablished = true;
        }

        if (previousClockTrusted == null || previousClockTrusted != trusted) {
            if (trusted) {
                logger.info("System clock is synchronised.");
            } else if (plausibleRtcTime) {
                logger.info("NTP is unavailable, but the RTC-enabled system clock is plausible; control can start.");
            } else if (Boolean.TRUE.equals(previousClockTrusted) && controlTimeEstablished) {
                logger.warn(
                        "System clock is no longer synchronised; control continues using the established system time.");
            } else if (controlTimeEstablished) {
                logger.info("NTP is unavailable; control continues using the established system time.");
            } else {
                logger.warn("System clock is not synchronised; control remains paused until synchronisation.");
            }
            previousClockTrusted = trusted;
        }
    }

    private static boolean isPlausibleClockTime(long currentTimeMillis) {
        return currentTimeMillis >= MIN_PLAUSIBLE_CLOCK_EPOCH_MILLIS;
    }
}
