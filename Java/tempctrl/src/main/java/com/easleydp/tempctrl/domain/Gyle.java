package com.easleydp.tempctrl.domain;

import static com.easleydp.tempctrl.domain.Gyle.LogFileDescriptor.buildLogFilename;
import static com.easleydp.tempctrl.domain.PropertyUtils.getBoolean;
import static com.easleydp.tempctrl.domain.PropertyUtils.getIntArray;
import static com.easleydp.tempctrl.domain.PropertyUtils.getInteger;
import static com.easleydp.tempctrl.domain.Utils.reduceUtcMillisPrecision;
import static com.easleydp.tempctrl.domain.optimise.RedundantValues.nullOutRedundantValues;
import static com.easleydp.tempctrl.domain.optimise.RedundantValues.removeRedundantIntermediateBeans;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.util.Assert;

import com.easleydp.tempctrl.domain.optimise.Smoother;
import com.easleydp.tempctrl.domain.optimise.Smoother.IntPropertyAccessor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SequenceWriter;

/**
 * NOTE: This is a stateful bean since it contains buffered readings.
 */
public class Gyle extends GyleDto {
    private static final Logger logger = LoggerFactory.getLogger(Gyle.class);

    /**
     * NDJSON is JSON (non-pretty printed) with a new line delimiter after each
     * line.
     */
    private static final String NDJSON_NEWLINE_DELIM = "\n";

    private static ObjectMapper ndjsonMapper = new ObjectMapper();
    private static ObjectWriter ndjsonObjectWriter = ndjsonMapper.writerFor(ChamberReadings.class)
            .withRootValueSeparator(NDJSON_NEWLINE_DELIM);

    private Smoother smoother;
    private LogBufferConfig logBufferConfig;
    private LogBuffer logBuffer;
    private TrendBuffer trendBuffer;
    private boolean firstReadingsCollected = false;
    private ChamberReadings latestChamberReadings;

    private long fileLastModified;
    @JsonIgnore
    public final Chamber chamber;
    @JsonIgnore
    public final Path gyleDir;
    @JsonIgnore
    public final int id;
    @JsonIgnore
    public final Path logsDir;

    private LogAnalysis logAnalysis;

    // NOTE: Keep this ctor lightweight since it's called more regularly than you
    // might imagine, i.e. via `Chamber.checkForGyleUpdates()`.
    public Gyle(Chamber chamber, Path gyleDir) {
        this.chamber = chamber;
        this.gyleDir = gyleDir;
        this.id = Integer.parseInt(gyleDir.getFileName().toString());
        this.logsDir = gyleDir.resolve("logs");

        int thresholdHeight = getInteger("readings.temp.smoothing.thresholdHeight", 2);
        int[] thresholdWidths = getIntArray("readings.temp.smoothing.thresholdWidths", null);
        smoother = thresholdWidths == null ? new Smoother(thresholdHeight)
                : new Smoother(thresholdHeight, thresholdWidths);

        logBufferConfig = new LogBufferConfig(getInteger("readings.gen1.readingsCount", 30),
                getBoolean("readings.optimise.smoothTemperatureReadings", true),
                getBoolean("readings.optimise.nullOutRedundantValues", true),
                getBoolean("readings.optimise.removeRedundantIntermediate", true));

        refresh();
    }

    @JsonIgnore
    public long getFileLastModified() {
        return fileLastModified;
    }

    public void refresh() {
        Path jsonFile = gyleDir.resolve("gyle.json");
        Assert.state(Files.exists(jsonFile), "gyle.json should exist");
        fileLastModified = jsonFile.toFile().lastModified();
        try {
            String json = FileUtils.readFileToString(jsonFile.toFile(), StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            BeanUtils.copyProperties(mapper.readValue(json, GyleDto.class), this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @JsonIgnore
    public TemperatureProfile getTemperatureProfileDomain() {
        TemperatureProfileDto dto = getTemperatureProfile();
        return new TemperatureProfile(dto);
    }

    @JsonIgnore
    public boolean isActive() {
        return getDtStarted() != null && getDtEnded() == null;
    }

    public ChamberParameters getChamberParameters(Date timeNow) {
        Long dtStarted = getDtStarted();
        long timeNowMs = timeNow.getTime();
        long millisSinceStart = dtStarted == null ? 0 : timeNowMs - dtStarted;
        logger.debug("millisSinceStart: {} (timeNowMs={}, dtStarted={})", millisSinceStart, timeNowMs, dtStarted);
        TemperatureProfile tp = getTemperatureProfileDomain();
        int gyleAgeHours = (int) (millisSinceStart / 1000L / 60 / 60);
        return new ChamberParameters(gyleAgeHours, tp.getTargetTempAt(millisSinceStart),
                tp.getTargetTempAt(millisSinceStart + 1000 * 60 * 60), chamber.gettMin(), chamber.gettMax(),
                chamber.isHasHeater(), chamber.getFridgeMinOnTimeMins(), chamber.getFridgeMinOffTimeMins(),
                chamber.getFridgeSwitchOnLagMins(), chamber.getKp(), chamber.getKi(), chamber.getKd(),
                isActive() ? getMode() : Mode.MONITOR_ONLY);
    }

    /**
     * Log readings (supplied from the chamber manager) and store (in memory buffer
     * if not persistently).
     *
     * @param gyle
     * @param timeNow
     *            - Supplied by caller for the sake of testability. In real life it
     *            will be equal to the actual time now.
     *
     *            Note: Recent readings are stored buffered in memory. When the
     *            buffer size limit is reached they're flushed to persistent storage
     *            (files on disk). The set of data files for the gyle are
     *            consolidated once in a while.
     * @throws IOException
     */
    public void logLatestReadings(ChamberReadings chamberReadings, Date timeNow) throws IOException {
        final int chamberId = chamber.getId();
        logger.debug("logLatestReadings() for chamber {} gyle {}", chamberId, gyleDir.getFileName());

        latestChamberReadings = chamberReadings;

        if (logAnalysis == null)
            logAnalysis = new LogAnalysis(); // Fail fast rather than leave this until first flush

        // NOTE: We don't flush the buffer as soon as it becomes full because then a
        // client keeping up-to-date by just consuming 'recent' records would likely
        // miss a record. Instead, we leave the buffer full then, on the next call to
        // this routine, check whether to flush before adding the first record of a new
        // buffer.

        // If the memory buffer is ready to be flushed, flush & release, and consolidate
        // log files as necessary.
        if (logBuffer != null) {
            if (logBuffer.isReadyToBeFlushed()) {
                logBuffer.flush(logsDir, logAnalysis);
                logBuffer = null;
                logAnalysis.maybeConsolidateLogFiles();
            } else {
                // Now that a little time has passed since the last consolidation, the redundant
                // gen1 files can be removed.
                logAnalysis.performAnyPostConsolidationCleanup();
            }
        }

        // Ensure we have a buffer and write the readings to it.
        if (logBuffer == null) {
            if (!firstReadingsCollected && PropertyUtils.getBoolean("readings.staggerFirstReadings", true)) {
                // Stagger each active gyle storing its first buffer by inflating the initial
                // reading count.
                logBuffer = new LogBuffer(timeNow, logBufferConfig.withInflatedReadingsCount(chamberId), smoother);
            } else {
                logBuffer = new LogBuffer(timeNow, logBufferConfig, smoother);
            }
        }
        logBuffer.add(chamberReadings, timeNow);

        // Lazy init rather than use ctor because ctor is called frequently (to see
        // whether latest gyle has been superseded).
        if (trendBuffer == null)
            trendBuffer = new TrendBuffer(chamber);
        trendBuffer.add(chamberReadings);

        firstReadingsCollected = true;
    }

    /** Forces flush and consolidation. */
    public void close() {
        if (logBuffer != null && !logBuffer.readingsList.isEmpty()) {
            logger.debug("Force flushing {} readings", logBuffer.readingsList.size());
            logBuffer.flush(logsDir, logAnalysis);
            logBuffer = null;
            logAnalysis.maybeConsolidateLogFiles();
            logAnalysis.performAnyPostConsolidationCleanup();
        }
    }

    /**
     * Returns the recent (i.e. buffered) readings in chronological order.
     */
    @JsonIgnore // In case this DTO subclass is ever serialised
    public List<ChamberReadings> getRecentReadingsList() {
        return logBuffer != null ? unmodifiableList(logBuffer.readingsList) : emptyList();
    }

    /**
     * Returns the readings log file paths in chronological order.
     */
    @JsonIgnore // In case this DTO subclass is ever serialised
    public List<Path> getReadingsLogFilePaths() {
        if (logAnalysis == null)
            logAnalysis = new LogAnalysis();

        // @formatter:off
        return logAnalysis.logFileDescriptors.stream()
            .map(lfd -> lfd.logFile)
            .collect(Collectors.toList());
        // @formatter:on
    }

    public void persist() throws IOException {
        Path jsonFile = gyleDir.resolve("gyle.json");
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        writer.writeValue(jsonFile.toFile(), this);
        // We don't need to do anything special having updated the JSON file;
        // CollectReadingsScheduler will detect.
    }

    /*
     * Support the sending of some kind of notification when it looks like the
     * fridge (or the heater if there is one) has been left switched off. Likewise,
     * sending a follow-up notification when this condition has cleared.
     *
     * The details of how the user is actually notified is left to the bean that
     * invokes this functionality.
     */

    private static final int IGNORE_FIRST_HOURS = PropertyUtils.getInteger("switchedOffCheck.ignoreFirstHours", 4);
    private static final int FRIDGE_ON_TIME_MINS = PropertyUtils.getInteger("switchedOffCheck.fridgeOnTimeMins", 5);
    private static final int HEATER_ON_TIME_MINS = PropertyUtils.getInteger("switchedOffCheck.heaterOnTimeMins", 10);

    // The return value for our `checkLeftSwitchedOff()` method.
    public enum LeftSwitchedOffDetectionAction {
        SEND_FRIDGE_LEFT_OFF, SEND_FRIDGE_NO_LONGER_LEFT_OFF, SEND_HEATER_LEFT_OFF, SEND_HEATER_NO_LONGER_LEFT_OFF
    }

    // Values for our internal state machine (see member variable
    // `leftSwitchedOffState`) that governs the lifecycle of values returned
    // from our checkSwitchedOff() method.
    private enum LeftSwitchedOffState {
        FRIDGE_LEFT_OFF, HEATER_LEFT_OFF, NEITHER_LEFT_OFF
    }

    private LeftSwitchedOffState leftSwitchedOffState = LeftSwitchedOffState.NEITHER_LEFT_OFF;

    /**
     * Checks whether it looks like the fridge or heater has been left switched off.
     * Principle of operation: if the fridge or heater has been on for a while then
     * there should have been a sympathetic change in the chamber temperature over
     * that period.
     *
     * @return An 'action' value if a notification needs to be sent, otherwise null.
     *         Note that while any given condition persists, this method returns
     *         null until that condition has changed.
     */
    public LeftSwitchedOffDetectionAction checkLeftSwitchedOff(Date timeNow) {
        Assert.state(isActive(), "checkLeftSwitchedOff() should only be called on active gyle");
        long dtStarted = getDtStarted();
        long timeNowMs = timeNow.getTime();
        long millisSinceStart = timeNowMs - dtStarted;
        int gyleAgeHours = (int) (millisSinceStart / 1000L / 60 / 60);
        if (gyleAgeHours >= IGNORE_FIRST_HOURS) {

            if (latestChamberReadings.getFridgeOn()) {
                // Fridge should be ON (as far as this app is concerned) but has it been left
                // OFF?
                if (leftSwitchedOffState != LeftSwitchedOffState.FRIDGE_LEFT_OFF) {
                    if (trendBuffer.getFridgeOnTimeMins() >= FRIDGE_ON_TIME_MINS + chamber.getFridgeSwitchOnLagMins()
                            && trendBuffer.gettChamberTrend(FRIDGE_ON_TIME_MINS) != Trend.DOWNWARDS) {
                        leftSwitchedOffState = LeftSwitchedOffState.FRIDGE_LEFT_OFF;
                        return LeftSwitchedOffDetectionAction.SEND_FRIDGE_LEFT_OFF;
                    }
                }
            } else {
                // This app has switched the fridge OFF.
                // If state is FRIDGE_LEFT_OFF then we can now transition to NEITHER_LEFT_OFF
                // and signal that the condition has cleared.
                if (leftSwitchedOffState == LeftSwitchedOffState.FRIDGE_LEFT_OFF) {
                    leftSwitchedOffState = LeftSwitchedOffState.NEITHER_LEFT_OFF;
                    return LeftSwitchedOffDetectionAction.SEND_FRIDGE_NO_LONGER_LEFT_OFF;
                }
            }

            if (chamber.isHasHeater()) {
                final int heaterThresholdPercent = 30;
                if (latestChamberReadings.getHeaterOutput() >= heaterThresholdPercent) {
                    // Heater should be ON (as far as this app is concerned) but is it switched OFF?
                    if (leftSwitchedOffState != LeftSwitchedOffState.HEATER_LEFT_OFF) {
                        if (trendBuffer.getHeaterOnTimeMins(heaterThresholdPercent) >= HEATER_ON_TIME_MINS
                                && trendBuffer.gettChamberTrend(HEATER_ON_TIME_MINS) != Trend.UPWARDS) {
                            leftSwitchedOffState = LeftSwitchedOffState.HEATER_LEFT_OFF;
                            return LeftSwitchedOffDetectionAction.SEND_HEATER_LEFT_OFF;
                        }
                    }
                } else {
                    // This app has switched the heater OFF.
                    // If state is HEATER_OFF_DETECTED then we can now signal that the condition has
                    // cleared.
                    if (leftSwitchedOffState == LeftSwitchedOffState.HEATER_LEFT_OFF) {
                        leftSwitchedOffState = LeftSwitchedOffState.NEITHER_LEFT_OFF;
                        return LeftSwitchedOffDetectionAction.SEND_HEATER_NO_LONGER_LEFT_OFF;
                    }
                }
            }
        }

        return null;
    }

    private class LogAnalysis {
        final int genMultiplier;
        final int maxGenerations;
        final List<LogFileDescriptor> logFileDescriptors;
        private List<LogFileDescriptor> awaitingCleanup = new ArrayList<>();

        LogAnalysis() {
            genMultiplier = PropertyUtils.getInteger("readings.gen.multiplier", 10);
            maxGenerations = PropertyUtils.getInteger("readings.gen.max", 4);
            Assert.state(genMultiplier >= 2, "readings.gen.multiplier must be at least 2");
            Assert.state(maxGenerations >= 2, "readings.gen.max must be at least 2");

            try {
                if (!Files.exists(logsDir))
                    Files.createDirectories(logsDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try (Stream<Path> stream = Files.walk(logsDir, 1)) {
                logFileDescriptors = stream.filter(Files::isRegularFile).map(LogFileDescriptor::new)
                        .collect(Collectors.toList());

                if (!logFileDescriptors.isEmpty()) {
                    logger.info(logFileDescriptors.size() + " gyle log file(s) found on start-up in " + logsDir);

                    // Sort chronologically by dtStart. In the case of a tie (which should only
                    // happen when consolidated files didn't get purged), put the latest
                    // generation first so the following redundant files can be conveniently
                    // removed (see next block).
                    Collections.sort(logFileDescriptors, new Comparator<LogFileDescriptor>() {
                        @Override
                        public int compare(LogFileDescriptor fd1, LogFileDescriptor fd2) {
                            int diff = fd1.dtStart - fd2.dtStart; // i.e. earliest dtStart first
                            // If we have a tie, put the highest gen first so the following
                            // redundant files can be conveniently removed (see "Purge any files ...").
                            if (diff == 0)
                                diff = fd2.generation - fd1.generation; // latest gen first
                            // If we still have a tie, put the highest dtEnded first so the following
                            // redundant files can be conveniently removed (see "Purge any files ...").
                            if (diff == 0)
                                diff = fd2.dtEnd - fd1.dtEnd; // i.e. latest dtEnd first
                            if (diff == 0) // Same filename twice can't happen!
                                throw new IllegalStateException(fd1.getFilename() + ", " + fd2.getFilename());
                            return diff;
                        }
                    });

                    // Purge any files that seem to have been consolidated. (They must have just
                    // missed being purged before the app last terminated.)
                    int lastDtEnd = Integer.MIN_VALUE;
                    int lastGeneration = Integer.MAX_VALUE;
                    for (Iterator<LogFileDescriptor> iter = logFileDescriptors.iterator(); iter.hasNext();) {
                        LogFileDescriptor fd = iter.next();

                        if (fd.dtEnd <= lastDtEnd) {
                            logger.warn("Purging redundant log file on start-up: {}", fd.getFilename());
                            Files.delete(fd.logFile);
                            iter.remove();
                        } else {
                            lastDtEnd = fd.dtEnd;

                            // This would be inexplicable but let's check anyway: Having sorted by dtStart
                            // ASC, generations should implicitly be sorted DESC.
                            if (fd.generation > lastGeneration) {
                                logger.error("Detected inexplicable log file on start-up: {}", fd.getFilename());
                            } else {
                                lastGeneration = fd.generation;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Creates a LogFileDescriptor for the supplied log file (assumed to be the
         * latest) and adds it to our collection.
         */
        void addLogFileDescriptor(Path logFile) {
            logFileDescriptors.add(new LogFileDescriptor(logFile));
        }

        void maybeConsolidateLogFiles() {
            int gen = 1;
            do {
                List<LogFileDescriptor> genNDescriptors = getGenNDescriptors(gen);
                if (genNDescriptors.size() < genMultiplier)
                    break;
                consolidateLogFiles(genNDescriptors, ++gen);
                awaitingCleanup.addAll(genNDescriptors);
            } while (gen < maxGenerations);
        }

        private void consolidateLogFiles(List<LogFileDescriptor> genNDescriptors, int gen) {
            logger.debug("Consolidating {} log files for gen {}", genNDescriptors.size(), gen);
            LogFileDescriptor first = genNDescriptors.get(0);
            LogFileDescriptor last = genNDescriptors.get(genNDescriptors.size() - 1);
            Path newLogFile = logsDir.resolve(buildLogFilename(gen, first.dtStart, last.dtEnd));

            byte[] buff = new byte[1024 * 8];
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(newLogFile.toFile()))) {
                for (LogFileDescriptor desc : genNDescriptors) {
                    try (InputStream in = new FileInputStream(desc.logFile.toFile())) {
                        IOUtils.copyLarge(in, out, buff);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            logAnalysis.addLogFileDescriptor(newLogFile);
        }

        public void performAnyPostConsolidationCleanup() {
            if (!awaitingCleanup.isEmpty()) {
                try {
                    logger.debug("Performing post-consolidation cleanup for {} log files", awaitingCleanup.size());
                    for (LogFileDescriptor desc : awaitingCleanup) {
                        Files.delete(desc.logFile);
                        boolean removed = logFileDescriptors.remove(desc);
                        Assert.state(removed, desc.logFile + " should be removed.");
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                awaitingCleanup = new ArrayList<>();
            }
        }

        private List<LogFileDescriptor> getGenNDescriptors(int gen) {
            // @formatter:off
            return logFileDescriptors.stream()
                .filter(ld -> ld.generation == gen)
                .collect(Collectors.toList());
            // @formatter:on
        }
    }

    private static final Pattern logFilePattern = Pattern.compile("^(\\d+)-(\\d+)-(\\d+)\\.ndjson$");

    static class LogFileDescriptor {
        final Path logFile;
        final int generation;
        final int dtStart;
        final int dtEnd;

        public LogFileDescriptor(Path logFile) {
            this.logFile = logFile;

            String logfileName = logFile.getFileName().toString();
            Matcher matcher = logFilePattern.matcher(logfileName);
            Assert.state(matcher.matches(), "Invalid log file name: " + logfileName);

            this.generation = Integer.parseInt(matcher.group(1), 10);
            this.dtStart = Integer.parseInt(matcher.group(2), 10);
            this.dtEnd = Integer.parseInt(matcher.group(3), 10);
        }

        public String getFilename() {
            return logFile.getFileName().toString();
        }

        public static final String sep = "-";

        public static String buildLogFilename(int generation, int dtStart, int dtEnd) {
            return generation + sep + dtStart + sep + dtEnd + ".ndjson";
        }

        public static String buildLogFilename(int generation, Date dtStart, Date dtEnd) {
            return buildLogFilename(generation, reduceUtcMillisPrecision(dtStart.getTime()),
                    reduceUtcMillisPrecision(dtEnd.getTime()));
        }
    }

    // Buffers readings ahead of being flushed to a log file.
    static class LogBuffer {
        private LogBufferConfig config;
        private Smoother smoother;
        private Date createdAt;
        private Date lastAddedAt;
        private List<ChamberReadings> readingsList = Collections.synchronizedList(new ArrayList<>());

        public LogBuffer(Date createdAt, LogBufferConfig config, Smoother smoother) {
            this.createdAt = createdAt;
            this.config = config;
            this.smoother = smoother;
        }

        // Default ctor needed for Jackson deserialisation
        public LogBuffer() {
        }

        public void add(ChamberReadings chamberReadings, Date addedAt) {
            readingsList.add(chamberReadings);
            lastAddedAt = addedAt;
        }

        @JsonIgnore
        public boolean isReadyToBeFlushed() {
            return readingsList.size() >= config.gen1ReadingsCount;
        }

        /** For Jackson */
        public List<ChamberReadings> getReadings() {
            return readingsList;
        }

        /**
         * Flush this buffer to disk file. Impl note: passing params rather than make
         * the class non-static because Jackson needs static class when deserialising.
         */
        public void flush(Path logsDir, LogAnalysis logAnalysis) {
            optimiseReadings();

            try {
                String logFileName = buildLogFilename(1, createdAt, lastAddedAt);
                Path logFile = logsDir.resolve(logFileName);
                Files.createFile(logFile);
                logAnalysis.addLogFileDescriptor(logFile);

                Writer writer = new StringWriter();
                try (SequenceWriter sw = ndjsonObjectWriter.writeValues(writer)) {
                    sw.writeAll(readingsList);
                }
                String ndjson = writer.toString() + NDJSON_NEWLINE_DELIM;
                Files.writeString(logFile, ndjson, StandardCharsets.UTF_8);
                // Java 8: Files.write(logFile, Collections.singleton(ndjson),
                // StandardCharsets.UTF_8);

                // No need to clear `readings`; the caller will now release this buffer.
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void optimiseReadings() {
            // Removes insignificant fluctuations in the temperature readings.
            // Must smooth before removing redundant records since the smoothing algorithm
            // assumes readings are taken with a fixed frequency.
            if (config.smoothTemperatureReadings)
                for (IntPropertyAccessor temperatureAccessor : ChamberReadings.allTemperatureAccessors)
                    smoother.smoothOutSmallFluctuations((List) readingsList, temperatureAccessor);

            // For each ChamberReadings property:
            // If some contiguous readings have a property P with same value V then null-out
            // all the subsequent values (so they won't be serialised).
            if (config.nullOutRedundantValues) {
                for (String propertyName : ChamberReadings.getNullablePropertyNames())
                    nullOutRedundantValues(readingsList, propertyName);

                // Given that the records have been fed through `nullOutRedundantValues()`, any
                // intermediate records where all the nullable properties have been set to null
                // are redundant.
                if (config.removeRedundantIntermediateReadings)
                    removeRedundantIntermediateBeans(readingsList, ChamberReadings.getNullablePropertyNames());
            }
        }
    }

    static class LogBufferConfig {
        final int gen1ReadingsCount;
        final boolean smoothTemperatureReadings;
        final boolean nullOutRedundantValues;
        final boolean removeRedundantIntermediateReadings;

        public LogBufferConfig(int gen1ReadingsCount, boolean smoothTemperatureReadings, boolean nullOutRedundantValues,
                boolean removeRedundantIntermediateReadings) {
            this.gen1ReadingsCount = gen1ReadingsCount;
            this.smoothTemperatureReadings = smoothTemperatureReadings;
            this.nullOutRedundantValues = nullOutRedundantValues;
            this.removeRedundantIntermediateReadings = removeRedundantIntermediateReadings;
        }

        public LogBufferConfig withInflatedReadingsCount(int extraReadingsCount) {
            return new LogBufferConfig(gen1ReadingsCount + extraReadingsCount, smoothTemperatureReadings,
                    nullOutRedundantValues, removeRedundantIntermediateReadings);
        }

    }

    private enum Trend {
        UPWARDS, STEADY, DOWNWARDS
    }

    // Saves the last several minutes worth of readings so we can (i) determine
    // how long the fridge or heater has been switched on and (ii) analyse the
    // temperature trend.
    private static class TrendBuffer {
        private List<ChamberReadings> fifo = new LinkedList<>();
        private final int maxSize;

        TrendBuffer(Chamber chamber) {
            // The buffer is a FIFO deque of recent records, sized in terms of time.
            int sizeInMinutes = Math.max(FRIDGE_ON_TIME_MINS + chamber.getFridgeSwitchOnLagMins(), HEATER_ON_TIME_MINS)
                    * 2;
            // Given our sample rate, transform that into a size in terms of number of
            // records.
            maxSize = sizeInMinutes * 60 * 1000 / PropertyUtils.getReadingsPeriodMillis();
            logger.debug("TrendBuffer maxSize is {}", maxSize);
        }

        public synchronized void add(ChamberReadings chamberReadings) {
            if (isFull()) {
                fifo.remove(0);
                Assert.state(!isFull(), "Removing one record should be sufficient so no longer full");
            }
            // Copy the record because of the potential for fields being nulled-out when
            // records are flushed to log file.
            fifo.add(new ChamberReadings(chamberReadings));
            logger.debug("TrendBuffer size is {} {}", fifo.size(), isFull() ? "(full)" : "");
        }

        public synchronized boolean isFull() {
            final int currentSize = fifo.size();
            Assert.state(currentSize <= maxSize, "Buffer should never be over full");
            return currentSize == maxSize;
        }

        /**
         * Working backwards from the latest record, determines for how long the fridge
         * has been on.
         *
         * If the fridge is not ON (according to the latest record), returns zero.
         * Otherwise, returns a value of at least 1 (signifying that the fridge has been
         * ON for a period greater than zero and <= 1 minute).
         *
         * If, having worked backwards from the latest record, the fridge still appears
         * to be ON on reaching the first (earliest) record then the corresponding
         * period is returned. A degenerate case: If the buffer is empty, returns 0.
         *
         * @return 0 if the fridge is not currently ON (according to the latest record),
         *         otherwise a value of at least 1.
         */
        public synchronized int getFridgeOnTimeMins() {
            if (!fifo.isEmpty()) {
                final int size = fifo.size();
                ChamberReadings lastRecord = fifo.get(size - 1);
                if (lastRecord.getFridgeOn()) {
                    ListIterator<ChamberReadings> li = fifo.listIterator(size); // Start just after the last element.
                    ChamberReadings record = null;
                    while (li.hasPrevious()) {
                        record = li.previous();
                        if (!record.getFridgeOn())
                            break;
                    }
                    Assert.state(record != null, "readings should not be null"); // ... given the checks we made above
                    return minutesDifferenceAtLeastOne(lastRecord, record);
                }
            } else {
                logger.debug("FIFO is empty");
            }
            return 0;
        }

        /**
         * As `getFridgeOnTimeMins()` but for heater.
         *
         * @param threshold
         *            Anything below the threshold is ignored (as if OFF).
         *
         * @return 0 if the heater is not currently ON and >= `threshold` (according to
         *         the latest record), otherwise a value of at least 1.
         */
        public synchronized int getHeaterOnTimeMins(int threshold) {
            if (!fifo.isEmpty()) {
                final int size = fifo.size();
                ChamberReadings lastRecord = fifo.get(size - 1);
                if (lastRecord.getHeaterOutput() >= threshold) {
                    ListIterator<ChamberReadings> li = fifo.listIterator(size); // Start just after the last element.
                    ChamberReadings record = null;
                    while (li.hasPrevious()) {
                        record = li.previous();
                        if (record.getHeaterOutput() < threshold)
                            break;
                    }
                    Assert.state(record != null, "readings should not be null"); // ... given the checks we made above
                    return minutesDifferenceAtLeastOne(lastRecord, record);
                }
            } else {
                logger.debug("FIFO is empty");
            }
            return 0;
        }

        private int minutesDifferenceAtLeastOne(ChamberReadings lastRecord, ChamberReadings earlierRecord) {
            long periodMs = Utils.restoreUtcMillisPrecision(lastRecord.getDt() - earlierRecord.getDt());
            int periodMins = (int) (periodMs / 1000L / 60);
            return periodMins == 0 ? 1 : periodMins; // Always return at least 1
        }

        /**
         * Analyses the tChamber field of the most recent accumulated records to see if
         * there's a trend.
         *
         * @param periodMins
         *            The period (starting backwards from the latest added record) over
         *            which to analyse the trend.
         *
         * @return The detected trend, or `STEADY` if none. If the buffer does not
         *         contain enough records to cover the specified period then `STEADY` is
         *         returned.
         *
         *         Note: If the specified period exceeds this buffers max capacity then
         *         an error is thrown.
         */
        public synchronized Trend gettChamberTrend(int periodMins) {
            if (!fifo.isEmpty()) {
                final int size = fifo.size();
                ChamberReadings firstRecord = fifo.get(0);
                ChamberReadings lastRecord = fifo.get(size - 1);
                long maxPeriodMs = Utils.restoreUtcMillisPrecision(lastRecord.getDt() - firstRecord.getDt());
                int maxPeriodMins = (int) (maxPeriodMs / 1000L / 60);
                if (maxPeriodMins > periodMins) {
                    int soughtDt = lastRecord.getDt() - Utils.reduceUtcMillisPrecision(periodMins * 1000L * 60);
                    ListIterator<ChamberReadings> li = fifo.listIterator(size); // Start just after the last element.
                    while (li.hasPrevious()) {
                        ChamberReadings record = li.previous();
                        if (record.getDt() <= soughtDt) {
                            if (record.gettChamber() < lastRecord.gettChamber())
                                return Trend.UPWARDS;
                            if (record.gettChamber() > lastRecord.gettChamber())
                                return Trend.DOWNWARDS;
                            return Trend.STEADY;
                        }
                    }
                    throw new IllegalStateException("Should never get here");
                } else if (isFull()) {
                    throw new IllegalStateException("Specified periodMins (" + periodMins + ") exceeds size of buffer ("
                            + size + ", or " + maxPeriodMins + " mins)");
                } else {
                    logger.debug("Buffer not sufficiently full for requested period ({} mins)", periodMins);
                }
            } else {
                logger.debug("FIFO is empty");
            }
            return Trend.STEADY;
        }
    }
}
