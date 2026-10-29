package com.easleydp.tempctrl.domain;

import static com.easleydp.tempctrl.domain.Utils.reduceUtcMillisPrecision;
import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.lang3.time.DateUtils.addMinutes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.Assert;
import org.springframework.util.FileSystemUtils;
import org.springframework.boot.context.properties.bind.Binder;

import com.easleydp.tempctrl.domain.Gyle.LeftSwitchedOffDetectionAction;
import com.easleydp.tempctrl.domain.Gyle.LogFileDescriptor;
import com.easleydp.tempctrl.dto.GyleDto;
import com.easleydp.tempctrl.spring.config.AppProperties;
import com.easleydp.tempctrl.spring.config.AppProperties.Readings;
import com.easleydp.tempctrl.spring.config.AppProperties.SwitchedOffCheck;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class GyleTests {
    private MockChamberManager chamberManagerSim;
    private ChamberRepository chambers;
    private Chamber chamber;
    private Gyle gyle;
    private Date startTime;
    private Date timeNow;
    private MockEnvironment env;
    private Readings readings;
    private AppProperties appProps;
    private int profileTransitionHours;
    private SwitchedOffCheck switchedOffCheck;

    private static final int gen1ReadingsCount = 10;
    private static final int genMultiplier = 4;
    private static final int maxGenerations = 3;

    private void collectReadings() throws IOException {
        collectReadings(null);
    }

    private void collectReadings(ReadingsMassager massager) throws IOException {
        ChamberReadings latestReadings = chamberManagerSim.collectReadings(chamber.getId(), timeNow);
        chamber.setLatestChamberReadings(massager != null ? massager.massage(latestReadings) : latestReadings);
        gyle.logLatestReadings(latestReadings, timeNow);
    }

    interface ReadingsMassager {
        // Returns the supplied value to support chaining.
        ChamberReadings massage(ChamberReadings chamberReadings);
    }

    @BeforeEach
    public void beforeEach() throws Exception {
        env = new MockEnvironment();
        env.setProperty("app.temp-profile.transition-hours", "2");
        env.setProperty("app.readings.gen.multiplier", "" + genMultiplier);
        env.setProperty("app.readings.gen.gen1-readings-count", "" + gen1ReadingsCount);
        env.setProperty("app.readings.gen.max", "" + maxGenerations);
        env.setProperty("app.readings.period-millis", "60000");
        env.setProperty("app.readings.temp.smoothing.threshold-height", "2");
        env.setProperty("app.readings.temp.smoothing.threshold-widths", "2, 1");
        env.setProperty("app.readings.optimise.smooth-temperature-readings", "" + false);
        env.setProperty("app.readings.optimise.null-out-redundant-values", "" + false);
        env.setProperty("app.readings.optimise.remove-redundant-intermediate", "" + false);
        env.setProperty("app.readings.stagger-first-readings", "" + false);
        env.setProperty("app.switched-off-check.period-minutes", "" + 5);
        env.setProperty("app.switched-off-check.ignore-first-hours", "" + 4);
        env.setProperty("app.switched-off-check.fridge-on-time-mins", "" + 5);
        env.setProperty("app.switched-off-check.heater-on-time-mins", "" + 10);
        appProps = Binder.get(env).bind("app", AppProperties.class).orElseGet(AppProperties::new);
        readings = appProps.getReadings();
        profileTransitionHours = appProps.getTempProfile().getTransitionHours();
        switchedOffCheck = appProps.getSwitchedOffCheck();

        Path dataDir = Paths.get(".", "src/test/resources/testData");
        Assert.state(Files.exists(dataDir), "data dir should exist.");
        chambers = new ChamberRepository(dataDir, appProps);

        // Remove any "logs" dirs left over from a previous test
        for (Chamber ch : chambers.getChambers())
            for (Gyle g : ch.getGyles())
                FileSystemUtils.deleteRecursively(g.gyleDir.resolve("logs"));

        Calendar c = Calendar.getInstance();
        c.set(2019, 0, 1, 0, 0);
        startTime = c.getTime();

        chamber = chambers.getChamberById(2);
        // Actually, getChamberById will already have checked not null.
        assertNotNull(chamber, "Chamber 2 should be found");
        Gyle latestGyle = chamber.getLatestGyle();
        assertNotNull(latestGyle, "There should be a latest gyle");
        assertFalse(latestGyle.isActive(), "There should be no active gyle");
        gyle = chamber.getGyleById(1);
        // Actually, getGyleById will already have checked not null.
        assertNotNull(gyle, "Chamber 2 gyle 1 should be found");
        gyle.setDtStarted(startTime.getTime()); // So now it's the active gyle
        chamberManagerSim = new MockChamberManager(startTime, gyle.getTemperatureProfileDomain(), readings);
    }

    @Test
    public void shouldCreateFirstGyleForChamberWithoutGyles(@TempDir Path tempDir) throws Exception {
        Path freshChamberDir = tempDir.resolve("1");
        Files.createDirectories(freshChamberDir.resolve("gyles"));
        Files.copy(chamber.getChamberDir().resolve("chamber.json"), freshChamberDir.resolve("chamber.json"));

        Chamber freshChamber = new Chamber(freshChamberDir, appProps);
        assertNull(freshChamber.getLatestGyle());

        Gyle firstGyle = freshChamber.constructNextGyle(chamber.getGyleById(1), "#%d - First gyle");

        assertEquals(1, firstGyle.id);
        assertEquals(firstGyle, freshChamber.getLatestGyle());
        assertTrue(Files.exists(freshChamberDir.resolve("gyles/1/gyle.json")));
    }

    @Test
    public void shouldBindTemperatureProfileTransitionHours() {
        assertEquals(2, profileTransitionHours);
    }

    /**
     * Using the ChamberManagerSimulator, drive the application to create a bunch of
     * data log files as if a non-test ChamberManager were being used. We'll check
     * the state of the data files (including consolidation) at various points along
     * the way.
     */
    @Test
    public void shouldCollectReadings() throws Exception {
        // Simulate taking a reading every minute for <gen1ReadingsCount> minutes.
        // Confirm no data file created up to this point.
        timeNow = startTime;
        for (int i = 0; i < gen1ReadingsCount; i++) {
            timeNow = addMinutes(timeNow, 1);
            collectReadings();
        }
        // Confirm no data file created up to this point (even though the buffer is
        // full).
        assertEquals(0, listLogFiles().size());

        // Take one more reading. Confirm the first gen1ReadingsCount readings are now
        // flushed to a file.
        String expectedLogFileName =
                // "<generation>-<dtStart>-<dtEnd>.ndjson"
                "1-" + reduceUtcMillisPrecision(addMinutes(startTime, 1), 30000) + "-"
                        + reduceUtcMillisPrecision(timeNow, 30000)
                        + ".ndjson";
        timeNow = addMinutes(timeNow, 1);
        collectReadings();
        List<LogFileDescriptor> logFileDescs = listLogFiles();
        assertEquals(1, logFileDescs.size());
        assertEquals(expectedLogFileName, logFileDescs.get(0).getFilename());
        assertReadingsLookOk(gen1ReadingsCount, logFileDescs.get(0).logFile);

        // It should take <gen1ReadingsCount> minutes until a second file appears
        startTime = addMinutes(startTime, gen1ReadingsCount);
        for (int i = 0; i < gen1ReadingsCount - 1; i++) {
            timeNow = addMinutes(timeNow, 1);
            collectReadings();
        }
        // Confirm second data file not created just yet.
        assertEquals(1, listLogFiles().size());

        // Take one more reading. Confirm all the readings are now flushed to a file.
        expectedLogFileName =
                // "<generation>-<dtStart>-<dtEnd>.ndjson"
                "1-" + reduceUtcMillisPrecision(addMinutes(startTime, 1), 30000) + "-"
                        + reduceUtcMillisPrecision(timeNow, 30000)
                        + ".ndjson";
        timeNow = addMinutes(timeNow, 1);
        collectReadings();
        logFileDescs = listLogFiles();
        assertEquals(2, logFileDescs.size());
        assertEquals(expectedLogFileName, logFileDescs.get(1).getFilename());

        assertReadingsLookOk(gen1ReadingsCount, logFileDescs.get(1).logFile);
    }

    @Test
    public void shouldStaggerFirstReadingsOfEachChamber() throws Exception {
        env.setProperty("app.readings.stagger-first-readings", "" + true);
        // TODO
    }

    @Test
    public void shouldRejectInvalidGyleState() {
        GyleDto invalid = new GyleDto();
        assertThrows(IllegalArgumentException.class, () -> invalid.validateRequiredState());

        invalid.setName("demo");
        invalid.setMode(Mode.AUTO);
        invalid.setTemperatureProfile(new com.easleydp.tempctrl.dto.TemperatureProfileDto(java.util.Arrays.asList(
                new com.easleydp.tempctrl.dto.PointDto(0, 150),
                new com.easleydp.tempctrl.dto.PointDto(2, 160))));
        invalid.setDtStarted(200L);
        invalid.setDtEnded(100L);
        assertThrows(IllegalArgumentException.class, () -> invalid.validateRequiredState());

        invalid.setDtEnded(null);
        invalid.setMode(Mode.HOLD);
        assertThrows(IllegalArgumentException.class, () -> invalid.validateRequiredState());
    }

    private void assertReadingsLookOk(int expectedCCount, Path logFile) throws IOException {
        List<ChamberReadings> readings = getReadings(logFile);
        assertEquals(expectedCCount, readings.size());

        // Confirm they're in chronological order
        int lastDt = Integer.MIN_VALUE;
        for (ChamberReadings r : readings) {
            assertTrue(r.getDt() > lastDt);
            lastDt = r.getDt();
        }
    }

    private List<LogFileDescriptor> listLogFiles() {
        // @formatter:off
        List<LogFileDescriptor> fileDescs = new ArrayList<>(
                listFiles(gyle.logsDir.toFile(), new String[] { "ndjson" }, false)).stream()
                        .map(f -> new LogFileDescriptor(f.toPath()))
                        .collect(Collectors.toList());
        // @formatter:on

        // Sort chronologically
        fileDescs.sort(new Comparator<LogFileDescriptor>() {
            @Override
            public int compare(LogFileDescriptor fd1, LogFileDescriptor fd2) {
                return fd1.dtStart - fd2.dtStart;
            }
        });

        return fileDescs;
    }

    private List<ChamberReadings> getReadings(Path logFile) throws IOException {
        String ndjson = FileUtils.readFileToString(logFile.toFile(), StandardCharsets.UTF_8);
        MappingIterator<ChamberReadings> iterator = new ObjectMapper().readerFor(ChamberReadings.class)
                .readValues(ndjson);
        return iterator.readAll();
    }

    /**
     *
     */
    @Test
    public void shouldPurgeRedundantLogFilesOnStartup() throws Exception {
        // Create some dummy pre-existing log files
        chamber = chambers.getChamberById(2);
        gyle = chamber.getGyleById(1);
        Path logsDir = gyle.gyleDir.resolve("logs");
        if (!Files.exists(logsDir))
            Files.createDirectories(logsDir);
        String[] logFileNames = new String[] { "1-163-181.ndjson", "1-183-201.ndjson", "1-203-221.ndjson",
                "1-223-241.ndjson", "2-163-241.ndjson", };
        for (String logFileName : logFileNames) {
            Path logFile = logsDir.resolve(logFileName);
            Files.createFile(logFile);
        }

        // Log files are analysed (and purged) lazily on taking the first set of
        // readings.
        timeNow = startTime;
        collectReadings();

        // All bar the gen2 file should have been purged
        List<LogFileDescriptor> logFileDescs = listLogFiles();
        assertEquals(1, logFileDescs.size());
        assertEquals("2-163-241.ndjson", logFileDescs.get(0).getFilename());
    }

    @Test
    public void shouldConsolidateGen1LogFiles() throws Exception {
        timeNow = startTime;
        for (int i = 0; i < genMultiplier - 1; i++)
            collectEnoughReadingsForOneGen1File(i == 0);
        assertEquals(genMultiplier - 1, listLogFiles().size());

        // On creation of the last file of gen1, not only should the new gen1 file
        // appear
        // but also the first gen2 file.
        collectEnoughReadingsForOneGen1File(false);
        assertEquals(genMultiplier + 1, listLogFiles().size());

        // On collecting the next set of readings, the original gen1 files should be
        // tidied away.
        collectEnoughReadingsForOneGen1File(false);
        assertEquals(2, listLogFiles().size());
    }

    @Test
    public void shouldConsolidateGen2LogFiles() throws Exception {
        timeNow = startTime;
        for (int i = 0; i < genMultiplier; i++)
            for (int j = 0; j < genMultiplier; j++)
                collectEnoughReadingsForOneGen1File(i == 0 && j == 0);
        assertEquals(genMultiplier * 2 + 1, listLogFiles().size());

        // On collecting the next set of readings, the original gen1 files should be
        // tidied away.
        collectEnoughReadingsForOneGen1File(false);
        List<LogFileDescriptor> logFileDescs = listLogFiles();
        assertEquals(2, logFileDescs.size());

        assertReadingsLookOk(gen1ReadingsCount * genMultiplier * genMultiplier, logFileDescs.get(0).logFile);
        assertReadingsLookOk(gen1ReadingsCount, logFileDescs.get(1).logFile);
    }

    @Test
    public void shouldConsolidateGen3LogFiles() throws Exception {
        timeNow = startTime;
        for (int i = 0; i < genMultiplier; i++)
            for (int j = 0; j < genMultiplier; j++)
                for (int k = 0; k < genMultiplier; k++)
                    collectEnoughReadingsForOneGen1File(i == 0 && j == 0 && k == 0);
        // Since maxGenerations is 3, shouldn't have consolidated the gen3 files a a
        // gen4.
        assertEquals(genMultiplier * maxGenerations, listLogFiles().size());

        // On collecting the next set of readings, the original gen1 files should be
        // tidied away.
        collectEnoughReadingsForOneGen1File(false);
        List<LogFileDescriptor> logFileDescs = listLogFiles();
        assertEquals(genMultiplier + 1, logFileDescs.size());

        assertReadingsLookOk(gen1ReadingsCount * genMultiplier * genMultiplier, logFileDescs.get(0).logFile);
        assertReadingsLookOk(gen1ReadingsCount * genMultiplier * genMultiplier,
                logFileDescs.get(logFileDescs.size() - 2).logFile);
        assertReadingsLookOk(gen1ReadingsCount, logFileDescs.get(logFileDescs.size() - 1).logFile);
    }

    private void collectEnoughReadingsForOneGen1File(boolean firstBuffer) throws IOException {
        for (int i = 0; i < gen1ReadingsCount; i++) {
            timeNow = addMinutes(timeNow, 1);
            collectReadings();
        }
        // If it's the first buffer we need to take one more reading to trip flushing
        // the buffer. (Gyle.collectReadings() doesn't flush the buffer as soon as it
        // becomes full because then a client keeping up-to-date by just consuming
        // 'recent' records would likely miss a record. Instead, it leaves the buffer
        // full then, on the next call to the routine, checks whether to flush before
        // adding the first record of a new buffer.
        if (firstBuffer) {
            timeNow = addMinutes(timeNow, 1);
            collectReadings();
        }
    }

    @Test
    public void leftFridgeOff() throws Exception {
        timeNow = startTime;

        final int tTarget = 0;
        int[] tChamber = { 0 };
        boolean[] fridgeOn = { false };
        ReadingsMassager massager = new ReadingsMassager() {
            @Override
            public ChamberReadings massage(ChamberReadings chamberReadings) {
                chamberReadings.setFridgeOn(fridgeOn[0]);
                chamberReadings.setHeaterOutput(0);
                chamberReadings.settTarget(tTarget);
                chamberReadings.settChamber(tChamber[0]);
                return chamberReadings;
            }
        };

        // First off we need to accumulate a few hours worth of readings because these
        // will be ignored by the analysis.
        int minutes = switchedOffCheck.getIgnoreFirstHours() * 60;
        for (int i = 0; i < minutes; i++) {
            timeNow = addMinutes(timeNow, 1);
            collectReadings(massager);
        }

        assertNull(gyle.checkLeftSwitchedOff(timeNow));

        // Next, run for a few minutes with fridge ON and tChamber ramping upwards
        // despite fridge being on.
        minutes = switchedOffCheck.getFridgeOnTimeMins() - 1;

        fridgeOn[0] = true;
        for (int i = 0; i < minutes; i++) {
            tChamber[0]++;
            timeNow = addMinutes(timeNow, 1);
            collectReadings(massager);
        }
        assertNull(gyle.checkLeftSwitchedOff(timeNow));

        // One more minute should trigger
        tChamber[0]++;
        timeNow = addMinutes(timeNow, 1);
        collectReadings(massager);
        assertEquals(LeftSwitchedOffDetectionAction.SEND_FRIDGE_LEFT_OFF, gyle.checkLeftSwitchedOff(timeNow));

        assertNull(gyle.checkLeftSwitchedOff(timeNow), "Action notification should NOT be sticky");

        for (int i = 0; i < minutes; i++) {
            tChamber[0]--;
            timeNow = addMinutes(timeNow, 1);
            collectReadings(massager);
        }
        // One final reading with fridge off
        fridgeOn[0] = false;
        timeNow = addMinutes(timeNow, 1);
        collectReadings(massager);
        assertEquals(LeftSwitchedOffDetectionAction.SEND_FRIDGE_NO_LONGER_LEFT_OFF, gyle.checkLeftSwitchedOff(timeNow));

        assertNull(gyle.checkLeftSwitchedOff(timeNow), "Action notification should NOT be sticky");
    }

    @Test
    public void leftHeaterOff() throws Exception {
        timeNow = startTime;

        final int tTarget = 175;
        int[] tBeer = { tTarget - 20 };
        int[] tChamber = { tTarget };
        int[] heaterOutput = { 0 };
        ReadingsMassager massager = new ReadingsMassager() {
            @Override
            public ChamberReadings massage(ChamberReadings chamberReadings) {
                chamberReadings.setHeaterOutput(heaterOutput[0]);
                chamberReadings.setFridgeOn(false);
                chamberReadings.settTarget(tTarget);
                chamberReadings.settBeer(tBeer[0]);
                chamberReadings.settChamber(tChamber[0]);
                return chamberReadings;
            }
        };

        // First off we need to accumulate a few hours worth of readings because these
        // will be ignored by the analysis.
        int minutes = switchedOffCheck.getIgnoreFirstHours() * 60;
        for (int i = 0; i < minutes; i++) {
            timeNow = addMinutes(timeNow, 1);
            collectReadings(massager);
        }

        assertNull(gyle.checkLeftSwitchedOff(timeNow));

        // Next, run for a several minutes with heater ON and tChamber ramping downwards
        // despite heater being on.
        minutes = switchedOffCheck.getHeaterOnTimeMins() - 1;

        heaterOutput[0] = 30;
        for (int i = 0; i < minutes; i++) {
            tChamber[0]--;
            timeNow = addMinutes(timeNow, 1);
            collectReadings(massager);
        }
        assertNull(gyle.checkLeftSwitchedOff(timeNow));

        // One more minute should trigger
        tChamber[0]--;
        timeNow = addMinutes(timeNow, 1);
        collectReadings(massager);
        assertEquals(LeftSwitchedOffDetectionAction.SEND_HEATER_LEFT_OFF, gyle.checkLeftSwitchedOff(timeNow));

        assertNull(gyle.checkLeftSwitchedOff(timeNow), "Action notification should NOT be sticky");

        for (int i = 0; i < minutes; i++) {
            tChamber[0]++;
            timeNow = addMinutes(timeNow, 1);
            collectReadings(massager);
        }
        // One final reading with tBeer now reaching target
        tBeer[0] = tTarget;
        timeNow = addMinutes(timeNow, 1);
        collectReadings(massager);
        assertEquals(LeftSwitchedOffDetectionAction.SEND_HEATER_NO_LONGER_LEFT_OFF, gyle.checkLeftSwitchedOff(timeNow));

        assertNull(gyle.checkLeftSwitchedOff(timeNow), "Action notification should NOT be sticky");
    }

    /**
     * Prove a Gyle object (which is a GyleDto) can be serialised to a JSON
     * representation of the DTO and then deserialised to a GyleDto without issue.
     * Thereby prove there is no need for a `toDto()` method.
     *
     * If this test fails it probably means you recently added a new field or getter
     * to the domain object and forgot to annotate `@JsonIgnore`.
     */
    @Test
    public void testDomainObjectSerialisation() throws JsonProcessingException {
        // Serialise
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        String json = writer.writeValueAsString(gyle);

        // Deserialise
        GyleDto dto = mapper.readValue(json, GyleDto.class);

        assertTrue(dto.equals(gyle));
    }

}
