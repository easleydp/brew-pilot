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
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.Assert;
import org.springframework.util.FileSystemUtils;

public class GyleDataGenerator
{
    private MockChamberManager chamberManagerSim;
    private ChamberRepository chambers;
    private Chamber chamber;
    private Gyle gyle;
    private Date startTime;
    private Date timeNow;
    private MockEnvironment env;

    private static final int gen1ReadingsCount = 10;
    private static final int genMultiplier = 4;
    private static final int maxGenerations = 3;

    private void collectReadings() throws IOException
    {
        chamberManagerSim.setNowTime(timeNow);
        gyle.collectReadings(chamberManagerSim, timeNow);
    }

    @BeforeEach
    public void beforeEach() throws Exception
    {
        env = new MockEnvironment();
        PropertyUtils.setEnv(env);

        Path dataDir = Paths.get(".", "src/test/resources/testData");
        Assert.state(Files.exists(dataDir), "data dir should exist.");
        chambers = new ChamberRepository(dataDir);

        // Remove any "logs" dirs left over from a previous test
        for (Chamber ch : chambers.getChambers())
            for (Gyle g : ch.getGyles())
                FileSystemUtils.deleteRecursively(g.gyleDir.resolve("logs"));
    }

    @Test
    /** From 4 weeks ago up until now. */
    public void shouldGenerateRealisticDataFor4WeekGyle() throws Exception
    {
        Date endTime = new Date();
        startTime = DateUtils.addWeeks(endTime, -4);

        chamber = chambers.getChamberById(2);
        gyle = chamber.getGyleById(1);
        assertNotNull(gyle, "Chamber 2 gyle 1 should be found");  // Actually, getGyleById will already have checked not null.
        gyle.setDtStarted(startTime.getTime());  // So now it's the active gyle
        chamberManagerSim = new MockChamberManager(startTime, gyle.getTemperatureProfile(), env);

        timeNow = startTime;
        while (timeNow.getTime() < endTime.getTime())
        {
            timeNow = addMinutes(timeNow, 1);
            collectReadings();
        }
    }


}
