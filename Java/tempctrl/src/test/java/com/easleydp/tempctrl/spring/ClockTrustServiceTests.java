package com.easleydp.tempctrl.spring;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClockTrustServiceTests {
    private final ClockTrustService clockTrustService = new ClockTrustService();

    @Test
    void trustsOnlyAffirmativeSynchronisationOutput() {
        clockTrustService.updateClockTrust("yes");
        assertTrue(clockTrustService.isClockTrusted());
        assertTrue(clockTrustService.isControlTimeEstablished());

        clockTrustService.updateClockTrust("no");
        assertFalse(clockTrustService.isClockTrusted());
        assertTrue(clockTrustService.isControlTimeEstablished());

        clockTrustService.updateClockTrust(null);
        assertFalse(clockTrustService.isClockTrusted());
        assertTrue(clockTrustService.isControlTimeEstablished());

        clockTrustService.updateClockTrust("unknown");
        assertFalse(clockTrustService.isClockTrusted());
        assertTrue(clockTrustService.isControlTimeEstablished());
    }

    @Test
    void pausesUntilTimeIsTrustedAfterUntrustedStartup() {
        ClockTrustService untrustedAtStartup = new ClockTrustService();
        untrustedAtStartup.updateClockTrust("no");
        assertFalse(untrustedAtStartup.isControlTimeEstablished());

        untrustedAtStartup.updateClockTrust("yes");
        assertTrue(untrustedAtStartup.isClockTrusted());
        assertTrue(untrustedAtStartup.isControlTimeEstablished());

        untrustedAtStartup.updateClockTrust("no");
        assertFalse(untrustedAtStartup.isClockTrusted());
        assertTrue(untrustedAtStartup.isControlTimeEstablished());
    }
}
