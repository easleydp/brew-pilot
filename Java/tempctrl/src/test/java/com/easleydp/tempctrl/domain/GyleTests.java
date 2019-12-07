package com.easleydp.tempctrl.domain;

import static com.easleydp.tempctrl.domain.Utils.*;
import static org.apache.commons.io.FileUtils.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.Assert;
import org.springframework.util.FileSystemUtils;

public class GyleTests
{
    private MockChamberManager chamberManagerSim;
    private Chambers chambers;
    private Chamber chamber;
    private Gyle gyle;
    private Date timeNow;

    private void collectReadings()
    {
        chamberManagerSim.setNowTime(timeNow);
        gyle.collectReadings(chamberManagerSim, timeNow);
    }

    @BeforeEach
    public void beforeEach() throws Exception
    {
        Path dataDir = Paths.get(".", "src/test/resources/testData");
        Assert.state(Files.exists(dataDir), "data dir should exist.");
        chambers = new Chambers(dataDir);

        // Remove any "logs" dirs left over from a previous test
        for (Chamber ch : chambers.getChambers())
            for (Gyle g : ch.getGyles())
                FileSystemUtils.deleteRecursively(g.gyleDir.resolve("logs"));
    }

    /**
     * Using the ChamberManagerSimulator, drive the application to create a bunch of data log
     * files as if a non-test ChamberManager were being used. We'll check the state of the
     * data files (including consolidation) at various points along the way.
     */
    @Test
    public void shouldCollectReadings()
    {
        Calendar c = Calendar.getInstance();
        c.set(2000, 0, 1, 0, 0);
        Date startTime = c.getTime();

        timeNow = startTime;

        chamber = chambers.getChamberById(2);
        assertNotNull(chamber, "Chamber 2 should be found");  // Actually, getChamberById will already have checked not null.
        assertNull(chamber.getActiveGyle(), "There should be no active gyle");
        gyle = chamber.getGyleById(1);
        assertNotNull(gyle, "Chamber 2 gyle 1 should be found");  // Actually, getGyleById will already have checked not null.
        gyle.setDtStarted(startTime.getTime());  // So now it's the active gyle
        chamberManagerSim = new MockChamberManager(startTime, gyle.getTemperatureProfile());

        final int gen1ReadingsCount = 30;
        // Simulate taking a reading at t0 then every minute for <gen1ReadingsCount - 1> minutes.
        // Confirm no data file created up to this point.
        collectReadings();
        for (int i = 0; i < gen1ReadingsCount - 1; i++)
        {
            timeNow = addMinutes(timeNow, 1);
            collectReadings();
        }
        // Confirm no data file created up to this point.
        assertEquals(0, listLogFiles().size());

        // Take a tenth reading at 10 minutes. Confirm all the readings are now flushed to a file.
        timeNow = addMinutes(timeNow, 1);
        collectReadings();
        String expectedLogFileName =
                // "<dataBlockSeqNo>-<generation>-<dtStart>-<dtEnd>.json"
                "1-1-" + reduceUtcMillisPrecision(startTime) + "-" + reduceUtcMillisPrecision(timeNow) + ".json";
        List<File> logFiles = listLogFiles();
        assertEquals(1, logFiles.size());
        assertEquals(expectedLogFileName, logFiles.get(0).getName());
    }

    private List<File> listLogFiles()
    {
        return new ArrayList<>(listFiles(gyle.logsDir.toFile(), new String[] {"json"}, false));
    }

    /**
     * When creating the first memory buffer after a restart there may be some existing log files.
     * In this case their filenames should be scanned to determine the next "dataBlockSeqNo".
     */
    @Test
    public void shouldDetermineNextDataBlockSeqNo()
    {
        //TODO
    }

    private static Date addMinutes(Date d, int mins)
    {
        return new Date(d.getTime() + mins * 60 * 1000);
    }

}
