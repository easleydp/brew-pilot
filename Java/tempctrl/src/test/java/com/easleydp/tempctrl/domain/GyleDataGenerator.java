package com.easleydp.tempctrl.domain;

import static org.apache.commons.lang3.time.DateUtils.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.util.Assert;
import org.springframework.util.FileSystemUtils;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.context.properties.bind.Binder;

import com.easleydp.tempctrl.spring.config.AppProperties;
import com.easleydp.tempctrl.spring.config.AppProperties.Readings;
import com.easleydp.tempctrl.spring.config.AppProperties.SwitchedOffCheck;

public class GyleDataGenerator {
    private MockChamberManager chamberManagerSim;
    private ChamberRepository chambers;
    private Chamber chamber;
    private Gyle gyle;
    private Date startTime;
    private Date timeNow;
    private Readings readings;
    private SwitchedOffCheck switchedOffCheck;

    private void collectReadings() throws IOException {
        ChamberReadings latestReadings = chamberManagerSim.collectReadings(chamber.getId(), timeNow);
        chamber.setLatestChamberReadings(latestReadings);
        gyle.logLatestReadings(latestReadings, timeNow);
    }

    @BeforeEach
    public void beforeEach() throws Exception {
        AppProperties appProperties = Binder.get(new MockEnvironment()).bind("app", AppProperties.class)
                .orElseGet(AppProperties::new);
        readings = appProperties.getReadings();
        switchedOffCheck = appProperties.getSwitchedOffCheck();

        Path dataDir = Paths.get(".", "src/test/resources/testData");
        Assert.state(Files.exists(dataDir), "data dir should exist.");
        chambers = new ChamberRepository(dataDir, readings, switchedOffCheck);

        // Remove any "logs" dirs left over from a previous test
        for (Chamber ch : chambers.getChambers())
            for (Gyle g : ch.getGyles())
                FileSystemUtils.deleteRecursively(g.gyleDir.resolve("logs"));
    }

    // @Test // SLOW TEST - only enable when needed.
    /** From 4 weeks ago up until now. */
    public void shouldGenerateRealisticDataFor4WeekGyle() throws Exception {
        Date endTime = new Date();
        startTime = DateUtils.addWeeks(endTime, -4);

        chamber = chambers.getChamberById(2);
        gyle = chamber.getGyleById(1);
        assertNotNull(gyle, "Chamber 2 gyle 1 should be found"); // Actually, getGyleById will already have checked not
                                                                 // null.
        gyle.setDtStarted(startTime.getTime()); // So now it's the active gyle
        chamberManagerSim = new MockChamberManager(startTime, gyle.getTemperatureProfileDomain(), readings);

        timeNow = startTime;
        while (timeNow.getTime() < endTime.getTime()) {
            timeNow = addMinutes(timeNow, 1);
            collectReadings();
        }
    }

}
