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

        clockTrustService.updateClockTrust("no");
        assertFalse(clockTrustService.isClockTrusted());

        clockTrustService.updateClockTrust(null);
        assertFalse(clockTrustService.isClockTrusted());

        clockTrustService.updateClockTrust("unknown");
        assertFalse(clockTrustService.isClockTrusted());
    }
}
