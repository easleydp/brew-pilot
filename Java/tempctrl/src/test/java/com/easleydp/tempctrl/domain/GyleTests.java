package com.easleydp.tempctrl.domain;

import static com.easleydp.tempctrl.domain.Utils.*;
import static org.apache.commons.io.FileUtils.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.Assert;
import org.springframework.util.FileSystemUtils;

import com.easleydp.tempctrl.domain.Gyle.LogFileDescriptor;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GyleTests
{
    private MockChamberManager chamberManagerSim;
    private Chambers chambers;
    private Chamber chamber;
    private Gyle gyle;
    private Date startTime;
    private Date timeNow;
    private MockEnvironment env;

    private static final int readingsPerGeneration = 10;

    private void collectReadings()
    {
        chamberManagerSim.setNowTime(timeNow);
        gyle.collectReadings(chamberManagerSim, timeNow);
    }

    @BeforeEach
    public void beforeEach() throws Exception
    {
        env = new MockEnvironment();
        env.setProperty("readings.periodMillis", "60000");
        env.setProperty("readings.gen1.readingsCount", "10");
        env.setProperty("readings.temp.smoothing.thresholdHeight", "2");
        env.setProperty("readings.temp.smoothing.thresholdWidths", "2, 1");
        env.setProperty("readings.optimise.smoothTemperatureReadings", "" + false);
        env.setProperty("readings.optimise.nullOutRedundantValues", "" + false);
        env.setProperty("readings.optimise.removeRedundantIntermediate", "" + false);

        Path dataDir = Paths.get(".", "src/test/resources/testData");
        Assert.state(Files.exists(dataDir), "data dir should exist.");
        chambers = new Chambers(dataDir, env);

        // Remove any "logs" dirs left over from a previous test
        for (Chamber ch : chambers.getChambers())
            for (Gyle g : ch.getGyles())
                FileSystemUtils.deleteRecursively(g.gyleDir.resolve("logs"));

        Calendar c = Calendar.getInstance();
        c.set(2000, 0, 1, 0, 0);
        startTime = c.getTime();

        chamber = chambers.getChamberById(2);
        assertNotNull(chamber, "Chamber 2 should be found");  // Actually, getChamberById will already have checked not null.
        assertNull(chamber.getActiveGyle(), "There should be no active gyle");
        gyle = chamber.getGyleById(1);
        assertNotNull(gyle, "Chamber 2 gyle 1 should be found");  // Actually, getGyleById will already have checked not null.
        gyle.setDtStarted(startTime.getTime());  // So now it's the active gyle
        chamberManagerSim = new MockChamberManager(startTime, gyle.getTemperatureProfile(), env);
    }

    /**
     * Using the ChamberManagerSimulator, drive the application to create a bunch of data log
     * files as if a non-test ChamberManager were being used. We'll check the state of the
     * data files (including consolidation) at various points along the way.
     */
    @Test
    public void shouldCollectReadings() throws Exception
    {
        // Simulate taking a reading every minute for <gen1ReadingsCount - 1> minutes.
        // Confirm no data file created up to this point.
        timeNow = startTime;
        for (int i = 0; i < readingsPerGeneration - 1; i++)
        {
            timeNow = addMinutes(timeNow, 1);
            collectReadings();
        }
        // Confirm no data file created up to this point.
        assertEquals(0, listLogFiles().size());

        // Take one more reading. Confirm all the readings are now flushed to a file.
        timeNow = addMinutes(timeNow, 1);
        collectReadings();
        String expectedLogFileName =
                // "<dataBlockSeqNo>-<generation>-<dtStart>-<dtEnd>.json"
                "1-1-" + reduceUtcMillisPrecision(addMinutes(startTime, 1)) + "-" + reduceUtcMillisPrecision(timeNow) + ".json";
        List<LogFileDescriptor> logFileDescs = listLogFiles();
        assertEquals(1, logFileDescs.size());
        assertEquals(expectedLogFileName, logFileDescs.get(0).getFilename());
        assertEquals(readingsPerGeneration, countReadings(logFileDescs.get(0).logFile));


        // It should take <gen1ReadingsCount> minutes until a second file appears
        startTime = addMinutes(startTime, readingsPerGeneration);
        for (int i = 0; i < readingsPerGeneration - 1; i++)
        {
            timeNow = addMinutes(timeNow, 1);
            collectReadings();
        }
        // Confirm second data file not created just yet.
        assertEquals(1, listLogFiles().size());

        // Take one more reading. Confirm all the readings are now flushed to a file.
        timeNow = addMinutes(timeNow, 1);
        collectReadings();
        expectedLogFileName =
                // "<dataBlockSeqNo>-<generation>-<dtStart>-<dtEnd>.json"
                "2-1-" + reduceUtcMillisPrecision(addMinutes(startTime, 1)) + "-" + reduceUtcMillisPrecision(timeNow) + ".json";
        logFileDescs = listLogFiles();
        assertEquals(2, logFileDescs.size());
        assertEquals(expectedLogFileName, logFileDescs.get(1).getFilename());

        assertEquals(readingsPerGeneration, countReadings(logFileDescs.get(1).logFile));
    }

    private List<LogFileDescriptor> listLogFiles()
    {
        List<LogFileDescriptor> fileDescs = new ArrayList<>(listFiles(gyle.logsDir.toFile(), new String[] {"json"}, false)).stream()
                .map(f -> new LogFileDescriptor(f.toPath()))
                .collect(Collectors.toList());

        // Sort chronologically (latest last)
        fileDescs.sort(new Comparator<LogFileDescriptor>() {
            @Override
            public int compare(LogFileDescriptor fd1, LogFileDescriptor fd2)
            {
                return fd1.dataBlockSeqNo - fd2.dataBlockSeqNo;
            }});

        return fileDescs;
    }

    private int countReadings(Path logFile) throws IOException
    {
        String json = FileUtils.readFileToString(logFile.toFile(), "UTF-8");
        ObjectMapper mapper = new ObjectMapper();
        Gyle.Buffer buffer = mapper.readValue(json, Gyle.Buffer.class);
        return buffer.getReadings().size();
    }

    /**
     * When creating the first memory buffer after a restart there may be some existing log files.
     * In this case their filenames should be scanned to determine the next "dataBlockSeqNo".
     * @throws Exception
     */
    @Test
    public void shouldDetermineNextDataBlockSeqNo() throws Exception
    {
        // Create a dummy pre-existing log file
        chamber = chambers.getChamberById(2);
        gyle = chamber.getGyleById(1);
        Path logsDir = gyle.gyleDir.resolve("logs");
        if (!Files.exists(logsDir))
            Files.createDirectories(logsDir);
        String logFileName = "7-1-12345-23456.json";
        Path logFile = logsDir.resolve(logFileName);
        Files.createFile(logFile);

        // Take enough readings for first file to appear
        timeNow = startTime;
        for (int i = 0; i < readingsPerGeneration; i++)
        {
            timeNow = addMinutes(timeNow, 1);
            collectReadings();
        }

        String expectedLogFileName =
                // "<dataBlockSeqNo>-<generation>-<dtStart>-<dtEnd>.json"
                "8-1-" + reduceUtcMillisPrecision(addMinutes(startTime, 1)) + "-" + reduceUtcMillisPrecision(timeNow) + ".json";
        List<LogFileDescriptor> logFileDescs = listLogFiles();
        assertEquals(2, logFileDescs.size());
        assertEquals(expectedLogFileName, logFileDescs.get(1).getFilename());
    }

    private static Date addMinutes(Date d, int mins)
    {
        return new Date(d.getTime() + mins * 60 * 1000);
    }

}
