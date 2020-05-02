package com.easleydp.tempctrl.domain;

import static com.easleydp.tempctrl.domain.Utils.*;
import static org.apache.commons.io.FileUtils.*;
import static org.apache.commons.lang3.time.DateUtils.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GyleTests
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
        env.setProperty("readings.gen.multiplier", "" + genMultiplier);
        env.setProperty("readings.gen1.readingsCount", "" + gen1ReadingsCount);
        env.setProperty("readings.gen.max", "" + maxGenerations);
        env.setProperty("readings.periodMillis", "60000");
        env.setProperty("readings.temp.smoothing.thresholdHeight", "2");
        env.setProperty("readings.temp.smoothing.thresholdWidths", "2, 1");
        env.setProperty("readings.optimise.smoothTemperatureReadings", "" + false);
        env.setProperty("readings.optimise.nullOutRedundantValues", "" + false);
        env.setProperty("readings.optimise.removeRedundantIntermediate", "" + false);
        env.setProperty("readings.staggerFirstReadings", "" + false);
        PropertyUtils.setEnv(env);

        Path dataDir = Paths.get(".", "src/test/resources/testData");
        Assert.state(Files.exists(dataDir), "data dir should exist.");
        chambers = new ChamberRepository(dataDir);

        // Remove any "logs" dirs left over from a previous test
        for (Chamber ch : chambers.getChambers())
            for (Gyle g : ch.getGyles())
                FileSystemUtils.deleteRecursively(g.gyleDir.resolve("logs"));

        Calendar c = Calendar.getInstance();
        c.set(2019, 0, 1, 0, 0);
        startTime = c.getTime();

        chamber = chambers.getChamberById(2);
        assertNotNull(chamber, "Chamber 2 should be found");  // Actually, getChamberById will already have checked not null.
        Gyle latestGyle = chamber.getLatestGyle();
        assertNotNull(latestGyle, "There should be a latest gyle");
        assertFalse(latestGyle.isActive(), "There should be no active gyle");
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
        // Simulate taking a reading every minute for <gen1ReadingsCount> minutes.
        // Confirm no data file created up to this point.
        timeNow = startTime;
        for (int i = 0; i < gen1ReadingsCount; i++)
        {
            timeNow = addMinutes(timeNow, 1);
            collectReadings();
        }
        // Confirm no data file created up to this point (even though the buffer is full).
        assertEquals(0, listLogFiles().size());

        // Take one more reading. Confirm the first gen1ReadingsCount readings are now flushed to a file.
        String expectedLogFileName =
                // "<generation>-<dtStart>-<dtEnd>.ndjson"
                "1-" + reduceUtcMillisPrecision(addMinutes(startTime, 1)) + "-" + reduceUtcMillisPrecision(timeNow) + ".ndjson";
        timeNow = addMinutes(timeNow, 1);
        collectReadings();
        List<LogFileDescriptor> logFileDescs = listLogFiles();
        assertEquals(1, logFileDescs.size());
        assertEquals(expectedLogFileName, logFileDescs.get(0).getFilename());
        assertReadingsLookOk(gen1ReadingsCount, logFileDescs.get(0).logFile);


        // It should take <gen1ReadingsCount> minutes until a second file appears
        startTime = addMinutes(startTime, gen1ReadingsCount);
        for (int i = 0; i < gen1ReadingsCount - 1; i++)
        {
            timeNow = addMinutes(timeNow, 1);
            collectReadings();
        }
        // Confirm second data file not created just yet.
        assertEquals(1, listLogFiles().size());

        // Take one more reading. Confirm all the readings are now flushed to a file.
        expectedLogFileName =
                // "<generation>-<dtStart>-<dtEnd>.ndjson"
                "1-" + reduceUtcMillisPrecision(addMinutes(startTime, 1)) + "-" + reduceUtcMillisPrecision(timeNow) + ".ndjson";
        timeNow = addMinutes(timeNow, 1);
        collectReadings();
        logFileDescs = listLogFiles();
        assertEquals(2, logFileDescs.size());
        assertEquals(expectedLogFileName, logFileDescs.get(1).getFilename());

        assertReadingsLookOk(gen1ReadingsCount, logFileDescs.get(1).logFile);
    }

    @Test
    public void shouldStaggerFirstReadingsOfEachChamber() throws Exception
    {
        env.setProperty("readings.staggerFirstReadings", "" + true);
        // TODO
    }

    private void assertReadingsLookOk(int expectedCCount, Path logFile) throws IOException
    {
        List<ChamberReadings> readings = getReadings(logFile);
        assertEquals(expectedCCount, readings.size());

        // Confirm they're in chronological order
        int lastDt = Integer.MIN_VALUE;
        for (ChamberReadings r : readings)
        {
            assertTrue(r.getDt() > lastDt);
            lastDt = r.getDt();
        }
    }

    private List<LogFileDescriptor> listLogFiles()
    {
        List<LogFileDescriptor> fileDescs = new ArrayList<>(listFiles(gyle.logsDir.toFile(), new String[] {"ndjson"}, false)).stream()
                .map(f -> new LogFileDescriptor(f.toPath()))
                .collect(Collectors.toList());

        // Sort chronologically
        fileDescs.sort(new Comparator<LogFileDescriptor>() {
            @Override
            public int compare(LogFileDescriptor fd1, LogFileDescriptor fd2)
            {
                return fd1.dtStart - fd2.dtStart;
            }});

        return fileDescs;
    }

    private List<ChamberReadings> getReadings(Path logFile) throws IOException
    {
        String ndjson = FileUtils.readFileToString(logFile.toFile(), StandardCharsets.UTF_8);
        MappingIterator<ChamberReadings> iterator = new ObjectMapper().readerFor(ChamberReadings.class).readValues(ndjson);
        return iterator.readAll();
    }

    /**
     *
     */
    @Test
    public void shouldPurgeRedundantLogFilesOnStartup() throws Exception
    {
        // Create some dummy pre-existing log files
        chamber = chambers.getChamberById(2);
        gyle = chamber.getGyleById(1);
        Path logsDir = gyle.gyleDir.resolve("logs");
        if (!Files.exists(logsDir))
            Files.createDirectories(logsDir);
        String[] logFileNames = new String[] {
                "1-163-181.ndjson",
                "1-183-201.ndjson",
                "1-203-221.ndjson",
                "1-223-241.ndjson",
                "2-163-241.ndjson",
        };
        for (String logFileName : logFileNames)
        {
            Path logFile = logsDir.resolve(logFileName);
            Files.createFile(logFile);
        }

        // Log files are analysed (and purged) lazily on taking the first set of readings.
        timeNow = startTime;
        collectReadings();

        // All bar the gen2 file should have been purged
        List<LogFileDescriptor> logFileDescs = listLogFiles();
        assertEquals(1, logFileDescs.size());
        assertEquals("2-163-241.ndjson", logFileDescs.get(0).getFilename());
    }

    @Test
    public void shouldConsolidateGen1LogFiles() throws Exception
    {
        timeNow = startTime;
        for (int i = 0; i < genMultiplier - 1; i++)
            collectEnoughReadingsForOneGen1File(i == 0);
        assertEquals(genMultiplier - 1, listLogFiles().size());

        // On creation of the last file of gen1, not only should the new gen1 file appear
        // but also the first gen2 file.
        collectEnoughReadingsForOneGen1File(false);
        assertEquals(genMultiplier + 1, listLogFiles().size());

        // On collecting the next set of readings, the original gen1 files should be tidied away.
        collectEnoughReadingsForOneGen1File(false);
        assertEquals(2, listLogFiles().size());
    }

    @Test
    public void shouldConsolidateGen2LogFiles() throws Exception
    {
        timeNow = startTime;
        for (int i = 0; i < genMultiplier; i++)
            for (int j = 0; j < genMultiplier; j++)
                collectEnoughReadingsForOneGen1File(i == 0 && j == 0);
        assertEquals(genMultiplier * 2 + 1, listLogFiles().size());

        // On collecting the next set of readings, the original gen1 files should be tidied away.
        collectEnoughReadingsForOneGen1File(false);
        List<LogFileDescriptor> logFileDescs = listLogFiles();
        assertEquals(2, logFileDescs.size());

        assertReadingsLookOk(gen1ReadingsCount * genMultiplier * genMultiplier, logFileDescs.get(0).logFile);
        assertReadingsLookOk(gen1ReadingsCount, logFileDescs.get(1).logFile);
    }

    @Test
    public void shouldConsolidateGen3LogFiles() throws Exception
    {
        timeNow = startTime;
        for (int i = 0; i < genMultiplier; i++)
            for (int j = 0; j < genMultiplier; j++)
                for (int k = 0; k < genMultiplier; k++)
                    collectEnoughReadingsForOneGen1File(i == 0 && j == 0 && k == 0);
        // Since maxGenerations is 3, shouldn't have consolidated the gen3 files a a gen4.
        assertEquals(genMultiplier * maxGenerations, listLogFiles().size());

        // On collecting the next set of readings, the original gen1 files should be tidied away.
        collectEnoughReadingsForOneGen1File(false);
        List<LogFileDescriptor> logFileDescs = listLogFiles();
        assertEquals(genMultiplier + 1, logFileDescs.size());

        assertReadingsLookOk(gen1ReadingsCount * genMultiplier * genMultiplier, logFileDescs.get(0).logFile);
        assertReadingsLookOk(gen1ReadingsCount * genMultiplier * genMultiplier, logFileDescs.get(logFileDescs.size() - 2).logFile);
        assertReadingsLookOk(gen1ReadingsCount, logFileDescs.get(logFileDescs.size() - 1).logFile);
    }

    private void collectEnoughReadingsForOneGen1File(boolean firstBuffer) throws IOException
    {
        for (int i = 0; i < gen1ReadingsCount; i++)
        {
            timeNow = addMinutes(timeNow, 1);
            collectReadings();
        }
        // If it's the first buffer we need to take one more reading to trip flushing the
        // buffer. (Gyle.collectReadings() doesn't flush the buffer as soon as it becomes
        // full because then a client keeping up-to-date by just consuming 'recent' records
        // would likely miss a record. Instead, it leaves the buffer full then, on the next
        // call to the routine, checks whether to flush before adding the first record of a
        // new buffer.
        if (firstBuffer)
        {
            timeNow = addMinutes(timeNow, 1);
            collectReadings();
        }
    }

}
