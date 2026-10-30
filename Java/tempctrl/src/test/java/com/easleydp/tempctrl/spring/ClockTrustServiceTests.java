package com.easleydp.tempctrl.spring;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClockTrustServiceTests {
    private final ClockTrustService clockTrustService = new ClockTrustService(false);

    @Test
    void trustStateFollowsNtpSynchronisationStatus() {
        clockTrustService.updateClockTrust(true, false, 0);
        assertTrue(clockTrustService.isClockTrusted());
        assertTrue(clockTrustService.isControlTimeEstablished());

        clockTrustService.updateClockTrust(false, false, 0);
        assertFalse(clockTrustService.isClockTrusted());
        assertTrue(clockTrustService.isControlTimeEstablished());
    }

    @Test
    void pausesUntilTimeIsTrustedAfterUntrustedStartup() {
        ClockTrustService untrustedAtStartup = new ClockTrustService(false);
        untrustedAtStartup.updateClockTrust(false, true, 0);
        assertFalse(untrustedAtStartup.isControlTimeEstablished());

        untrustedAtStartup.updateClockTrust(true, false, 0);
        assertTrue(untrustedAtStartup.isClockTrusted());
        assertTrue(untrustedAtStartup.isControlTimeEstablished());

        untrustedAtStartup.updateClockTrust(false, false, 0);
        assertFalse(untrustedAtStartup.isClockTrusted());
        assertTrue(untrustedAtStartup.isControlTimeEstablished());
    }

    @Test
    void rtcAllowsPlausibleClockAtStartupWhenNtpIsUnavailable() {
        ClockTrustService rtcEnabled = new ClockTrustService(true);
        long plausibleTime = 1_767_225_600_000L; // 2026-01-01 UTC

        rtcEnabled.updateClockTrust(false, true, plausibleTime);

        assertFalse(rtcEnabled.isClockTrusted());
        assertTrue(rtcEnabled.isControlTimeEstablished());
    }

    @Test
    void rtcDoesNotAllowImplausibleClockAtStartup() {
        ClockTrustService rtcEnabled = new ClockTrustService(true);

        rtcEnabled.updateClockTrust(false, true, 0);

        assertFalse(rtcEnabled.isClockTrusted());
        assertFalse(rtcEnabled.isControlTimeEstablished());
    }
}
