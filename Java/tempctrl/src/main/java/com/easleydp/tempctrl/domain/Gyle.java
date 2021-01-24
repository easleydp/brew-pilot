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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.easleydp.tempctrl.domain.optimise.Smoother;
import com.easleydp.tempctrl.domain.optimise.Smoother.IntPropertyAccessor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SequenceWriter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.util.Assert;

public class Gyle extends GyleDto
{
    private static final Logger logger = LoggerFactory.getLogger(Gyle.class);

    /** NDJSON is JSON (non-pretty printed) with a new line delimiter after each line. */
    private static final String NDJSON_NEWLINE_DELIM = "\n";

    private static ObjectMapper ndjsonMapper = new ObjectMapper();
    private static ObjectWriter ndjsonObjectWriter = ndjsonMapper.writerFor(ChamberReadings.class).withRootValueSeparator(NDJSON_NEWLINE_DELIM);

    private Smoother smoother;
    private BufferConfig bufferConfig;
    private Buffer buffer;
    private boolean firstReadingsCollected = false;
    private ChamberReadings latestChamberReadings;

    private long fileLastModified;
    public final Chamber chamber;
    public final Path gyleDir;
    public final int id;
    public final Path logsDir;

    private LogAnalysis logAnalysis;

    public Gyle(Chamber chamber, Path gyleDir)
    {
        this.chamber = chamber;
        this.gyleDir = gyleDir;
        this.id = Integer.parseInt(gyleDir.getFileName().toString());
        this.logsDir = gyleDir.resolve("logs");

        int thresholdHeight = getInteger("readings.temp.smoothing.thresholdHeight", 2);
        int[] thresholdWidths = getIntArray("readings.temp.smoothing.thresholdWidths", null);
        smoother = thresholdWidths == null ?
                new Smoother(thresholdHeight) : new Smoother(thresholdHeight, thresholdWidths);

        bufferConfig = new BufferConfig(
                getInteger("readings.gen1.readingsCount", 30),
                getBoolean("readings.optimise.smoothTemperatureReadings", true),
                getBoolean("readings.optimise.nullOutRedundantValues", true),
                getBoolean("readings.optimise.removeRedundantIntermediate", true));

        refresh();
    }

    public int getId()
    {
        return id;
    }

	public long getFileLastModified() {
		return fileLastModified;
	}

	public void refresh() {
        Path jsonFile = gyleDir.resolve("gyle.json");
        Assert.state(Files.exists(jsonFile), "gyle.json should exist");
        fileLastModified = jsonFile.toFile().lastModified();
        try
        {
            String json = FileUtils.readFileToString(jsonFile.toFile(), StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            BeanUtils.copyProperties(mapper.readValue(json, GyleDto.class), this);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
	}

    @Override
    public TemperatureProfile getTemperatureProfile()
    {
        TemperatureProfileDto dto = super.getTemperatureProfile();
        return new TemperatureProfile(dto);
    }

    public boolean isActive()
    {
        return getDtStarted() != null  &&  getDtEnded() == null;
    }

    public ChamberParameters getChamberParameters(Date timeNow)
    {
    	Long dtStarted = getDtStarted();
        long timeNowMs = timeNow.getTime();
        long millisSinceStart = dtStarted == null || dtStarted < 0 ? 0 : timeNowMs - dtStarted;
        logger.debug("millisSinceStart: " + millisSinceStart + "(timeNowMs=" + timeNowMs + ", dtStarted=" + dtStarted + ")");
        TemperatureProfile tp = getTemperatureProfile();
        int gyleAgeHours = dtStarted == null ? -1 : (int) (millisSinceStart / (1000L * 60 * 60));
        return new ChamberParameters(gyleAgeHours, tp.getTargetTempAt(millisSinceStart), tp.getTargetTempAt(millisSinceStart + 1000 * 60 * 60),
                chamber.gettMin(), chamber.gettMax(), chamber.isHasHeater(),
                chamber.getFridgeMinOnTimeMins(), chamber.getFridgeMinOffTimeMins(), chamber.getFridgeSwitchOnLagMins(),
                chamber.getKp(), chamber.getKi(), chamber.getKd(),
                isActive() ? getMode() : Mode.MONITOR_ONLY);
    }

    /**
     * Get readings from the chamber manager and store (in memory buffer if not persistently).
     *
     * @param gyle
     * @param timeNow - Supplied by caller for the sake of testability. In real life it will be
     *                  equal to the actual time now.
     *
     * Note: Recent readings are stored buffered in memory. When the buffer size limit is reached
     * they're flushed to persistent storage (files on disk). The set of data files for the gyle
     * are consolidated once in a while.
     * @throws IOException
     */
    public void collectReadings(ChamberManager chamberManager, Date timeNow) throws IOException
    {
        final int chamberId = chamber.getId();
        logger.debug("collectReadings() for chamber " + chamberId);

        ChamberReadings chamberReadings = chamberManager.getReadings(chamberId, timeNow);
        if (logger.isDebugEnabled())
        {
            logger.debug("Chamber " + chamberId + " readings: " + chamberReadings.toString());
        }
        latestChamberReadings = chamberReadings;

        if (logAnalysis == null)
            logAnalysis = new LogAnalysis();  // Fail fast rather than leave this until first flush

        // NOTE: We don't flush the buffer as soon as it becomes full because then a client
        // keeping up-to-date by just consuming 'recent' records would likely miss a record.
        // Instead, we leave the buffer full then, on the next call to this routine, check
        // whether to flush before adding the first record of a new buffer.

        // If the memory buffer is ready to be flushed, flush & release, and consolidate log files as necessary.
        if (buffer != null)
        {
            if (buffer.isReadyToBeFlushed())
            {
                buffer.flush(logsDir, logAnalysis);
                buffer = null;
                logAnalysis.maybeConsolidateLogFiles();
            }
            else
            {
                // Now that a little time has passed since the last consolidation, the redundant gen1
                // files can be removed.
                logAnalysis.performAnyPostConsolidationCleanup();
            }
        }

        // Ensure we have a buffer and write the readings to it.
        if (buffer == null)
        {
            if (!firstReadingsCollected && PropertyUtils.getBoolean("readings.staggerFirstReadings", true))
            {
                // Stagger each active gyle storing its first buffer by inflating the initial reading count.
                buffer = new Buffer(timeNow, bufferConfig.withInflatedReadingsCount(chamberId), smoother);
            }
            else
            {
                buffer = new Buffer(timeNow, bufferConfig, smoother);
            }
        }
        buffer.add(chamberReadings, timeNow);

        firstReadingsCollected = true;
    }

    /** Forces flush and consolidation. */
    public void close()
    {
        if (buffer != null && !buffer.readingsList.isEmpty())
        {
            logger.debug("Force flushing " + buffer.readingsList.size() + " readings");
            buffer.flush(logsDir, logAnalysis);
            buffer = null;
            logAnalysis.maybeConsolidateLogFiles();
            logAnalysis.performAnyPostConsolidationCleanup();
        }
    }

    public ChamberReadings getLatestReadings()
    {
        return latestChamberReadings;
    }

    /**
     * Returns the recent (i.e. buffered) readings in chronological order.
     */
    public List<ChamberReadings> getRecentReadings()
    {
        return buffer != null ? unmodifiableList(buffer.readingsList) : emptyList();
    }

    /**
     * Returns the readings log file paths in chronological order.
     */
    public List<Path> getReadingsLogFilePaths()
    {
        if (logAnalysis == null)
            logAnalysis = new LogAnalysis();

        return logAnalysis.logFileDescriptors.stream()
                .map(lfd -> lfd.logFile)
                .collect(Collectors.toList());
    }

	public void persist() throws IOException {
        Path jsonFile = gyleDir.resolve("gyle.json");
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        writer.writeValue(jsonFile.toFile(), this.toDto());
        // We don't need to do anything special having updated the JSON file; CollectReadingsScheduler will detect.
	}

	public GyleDto toDto() {
		return new GyleDto(getName(), getTemperatureProfile(), getDtStarted(), getDtEnded(), getMode());
	}

    private class LogAnalysis
    {
        final int genMultiplier;
        final int maxGenerations;
        final List<LogFileDescriptor> logFileDescriptors;
        private List<LogFileDescriptor> awaitingCleanup = new ArrayList<>();

        LogAnalysis()
        {
            genMultiplier = PropertyUtils.getInteger("readings.gen.multiplier", 10);
            maxGenerations = PropertyUtils.getInteger("readings.gen.max", 4);
            Assert.state(genMultiplier >= 2, "readings.gen.multiplier must be at least 2");
            Assert.state(maxGenerations >= 2, "readings.gen.max must be at least 2");

            try
            {
                if (!Files.exists(logsDir))
                    Files.createDirectories(logsDir);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }

            try (Stream<Path> stream = Files.walk(logsDir, 1)) {
                logFileDescriptors = stream
                        .filter(Files::isRegularFile)
                        .map(LogFileDescriptor::new)
                        .collect(Collectors.toList());

                if (!logFileDescriptors.isEmpty())
                {
                    logger.info(logFileDescriptors.size() + " gyle log file(s) found on start-up in " + logsDir);

                    // Sort chronologically by dtStart. In the case of a tie (which should only
                    // happen when consolidated files didn't get purged), put the latest
                    // generation first so the following redundant files can be conveniently
                    // removed (see next block).
                    Collections.sort(logFileDescriptors, new Comparator<LogFileDescriptor>() {
                        @Override
                        public int compare(LogFileDescriptor fd1, LogFileDescriptor fd2)
                        {
                            int diff = fd1.dtStart - fd2.dtStart;  // i.e. earliest dtStart first
                            // If we have a tie, put the highest gen first so the following
                            // redundant files can be conveniently removed (see "Purge any files ...").
                            if (diff == 0)
                                diff = fd2.generation - fd1.generation;  // latest gen first
                            // If we still have a tie, put the highest dtEnded first so the following
                            // redundant files can be conveniently removed (see "Purge any files ...").
                            if (diff == 0)
                                diff = fd2.dtEnd - fd1.dtEnd;  // i.e. latest dtEnd first
                            if (diff == 0)  // Same filename twice can't happen!
                                throw new IllegalStateException(fd1.getFilename() + ", " + fd2.getFilename());
                            return diff;
                        }
                    });

                    // Purge any files that seem to have been consolidated. (They must have just
                    // missed being purged before the app last terminated.)
                    int lastDtEnd = Integer.MIN_VALUE;
                    int lastGeneration = Integer.MAX_VALUE;
                    for (Iterator<LogFileDescriptor> iter = logFileDescriptors.iterator(); iter.hasNext();)
                    {
                        LogFileDescriptor fd = iter.next();

                        if (fd.dtEnd <= lastDtEnd)
                        {
                            logger.warn("Purging redundant log file on start-up: " + fd.getFilename());
                            Files.delete(fd.logFile);
                            iter.remove();
                        }
                        else
                        {
                            lastDtEnd = fd.dtEnd;

                            // This would be inexplicable but let's check anyway: Having sorted by dtStart ASC,
                            // generations should implicitly be sorted DESC.
                            if (fd.generation > lastGeneration)
                            {
                                logger.error("Detected inexplicable log file on start-up: " + fd.getFilename());
                            }
                            else
                            {
                                lastGeneration = fd.generation;
                            }
                        }
                    }
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        /**
         * Creates a LogFileDescriptor for the supplied log file (assumed to be the latest)
         * and adds it to our collection.
         */
        void addLogFileDescriptor(Path logFile)
        {
            logFileDescriptors.add(new LogFileDescriptor(logFile));
        }

        void maybeConsolidateLogFiles()
        {
            int gen = 1;
            do {
                List<LogFileDescriptor> genNDescriptors = getGenNDescriptors(gen);
                if (genNDescriptors.size() < genMultiplier)
                    break;
                consolidateLogFiles(genNDescriptors, ++gen);
                awaitingCleanup.addAll(genNDescriptors);
            } while (gen < maxGenerations);
        }
        private void consolidateLogFiles(List<LogFileDescriptor> genNDescriptors, int gen)
        {
            logger.debug("Consolidating " + genNDescriptors.size() + " log files for gen " + gen);
            LogFileDescriptor first = genNDescriptors.get(0);
            LogFileDescriptor last = genNDescriptors.get(genNDescriptors.size() - 1);
            Path newLogFile = logsDir.resolve(buildLogFilename(gen, first.dtStart, last.dtEnd));

            byte[] buff = new byte[1024 * 8];
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(newLogFile.toFile())))
            {
                for (LogFileDescriptor desc : genNDescriptors)
                {
                    try (InputStream in = new FileInputStream(desc.logFile.toFile()))
                    {
                        IOUtils.copyLarge(in, out, buff);
                    }
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }

            logAnalysis.addLogFileDescriptor(newLogFile);
        }

        public void performAnyPostConsolidationCleanup()
        {
            if (!awaitingCleanup.isEmpty())
            {
                try
                {
                    logger.debug("Performing post-consolidation cleanup for " + awaitingCleanup.size() + " log files");
                    for (LogFileDescriptor desc : awaitingCleanup)
                    {
                        Files.delete(desc.logFile);
                        boolean removed = logFileDescriptors.remove(desc);
                        Assert.state(removed, desc.logFile + " should be removed.");
                    }
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
                awaitingCleanup = new ArrayList<>();
            }
        }

        private List<LogFileDescriptor> getGenNDescriptors(int gen)
        {
            return logFileDescriptors.stream()
                    .filter(ld -> ld.generation == gen)
                    .collect(Collectors.toList());
        }
    }

    private static final Pattern logFilePattern = Pattern.compile("^(\\d+)-(\\d+)-(\\d+)\\.ndjson$");

    static class LogFileDescriptor
    {
        final Path logFile;
        final int generation;
        final int dtStart;
        final int dtEnd;

        public LogFileDescriptor(Path logFile)
        {
            this.logFile = logFile;

            String logfileName = logFile.getFileName().toString();
            Matcher matcher = logFilePattern.matcher(logfileName);
            Assert.state(matcher.matches(), "Invalid log file name: " + logfileName);

            this.generation = Integer.parseInt(matcher.group(1), 10);
            this.dtStart = Integer.parseInt(matcher.group(2), 10);
            this.dtEnd = Integer.parseInt(matcher.group(3), 10);
        }

        public String getFilename()
        {
            return logFile.getFileName().toString();
        }

        public static final String sep = "-";
        public static String buildLogFilename(int generation, int dtStart, int dtEnd)
        {
            return generation + sep + dtStart + sep + dtEnd + ".ndjson";
        }
        public static String buildLogFilename(int generation, Date dtStart, Date dtEnd)
        {
            return buildLogFilename(generation,
                    reduceUtcMillisPrecision(dtStart.getTime()), reduceUtcMillisPrecision(dtEnd.getTime()));
        }
    }

    static class Buffer
    {
        private BufferConfig config;
        private Smoother smoother;
        private Date createdAt;
        private Date lastAddedAt;
        private List<ChamberReadings> readingsList = Collections.synchronizedList(new ArrayList<>());

        public Buffer(Date createdAt, BufferConfig config, Smoother smoother)
        {
            this.createdAt = createdAt;
            this.config = config;
            this.smoother = smoother;
        }

        /** Jackson needs a default ctor */
        public Buffer() {}

        public void add(ChamberReadings chamberReadings, Date addedAt)
        {
            readingsList.add(chamberReadings);
            lastAddedAt = addedAt;
        }

        @JsonIgnore
        public boolean isReadyToBeFlushed()
        {
            return readingsList.size() >= config.gen1ReadingsCount;
        }

        /** For Jackson */
        public List<ChamberReadings> getReadings()
        {
            return readingsList;
        }

        /**
         * Flush this buffer to disk file.
         * Impl note: passing params rather than make the class non-static because Jackson needs
         * static class when deserialising.
         */
        public void flush(Path logsDir, LogAnalysis logAnalysis)
        {
            optimiseReadings();

            try
            {
                String logFileName = buildLogFilename(1, createdAt, lastAddedAt);
                Path logFile = logsDir.resolve(logFileName);
                Files.createFile(logFile);
                logAnalysis.addLogFileDescriptor(logFile);

                Writer writer = new StringWriter();
                try (SequenceWriter sw = ndjsonObjectWriter.writeValues(writer))
                {
                    sw.writeAll(getReadings());
                }
                String ndjson = writer.toString() + NDJSON_NEWLINE_DELIM;
                Files.writeString(logFile, ndjson, StandardCharsets.UTF_8);
                // Java 8: Files.write(logFile, Collections.singleton(ndjson), StandardCharsets.UTF_8);

                // No need to clear `readings`; the caller will now release this buffer.
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        private void optimiseReadings()
        {
            // Removes insignificant fluctuations in the temperature readings.
            // Must smooth before removing redundant records since the smoothing algorithm assumes readings
            // are taken with a fixed frequency.
            if (config.smoothTemperatureReadings)
                for (IntPropertyAccessor temperatureAccessor : ChamberReadings.allTemperatureAccessors)
                    smoother.smoothOutSmallFluctuations((List) readingsList, temperatureAccessor);

            // For each ChamberReadings property:
            // If some contiguous readings have a property P with same value V then null-out
            // all the subsequent values (so they won't be serialised).
            if (config.nullOutRedundantValues)
            {
                for (String propertyName : ChamberReadings.getNullablePropertyNames())
                    nullOutRedundantValues(readingsList, propertyName);

                // Given that the records have been fed through `nullOutRedundantValues()`, any intermediate
                // records where all the nullable properties have been set to null are redundant.
                if (config.removeRedundantIntermediateReadings)
                    removeRedundantIntermediateBeans(readingsList, ChamberReadings.getNullablePropertyNames());
            }
        }
    }

    static class BufferConfig
    {
        final int gen1ReadingsCount;
        final boolean smoothTemperatureReadings;
        final boolean nullOutRedundantValues;
        final boolean removeRedundantIntermediateReadings;

        public BufferConfig(int gen1ReadingsCount, boolean smoothTemperatureReadings,
                boolean nullOutRedundantValues, boolean removeRedundantIntermediateReadings)
        {
            this.gen1ReadingsCount = gen1ReadingsCount;
            this.smoothTemperatureReadings = smoothTemperatureReadings;
            this.nullOutRedundantValues = nullOutRedundantValues;
            this.removeRedundantIntermediateReadings = removeRedundantIntermediateReadings;
        }

        public BufferConfig withInflatedReadingsCount(int extraReadingsCount)
        {
            return new BufferConfig(gen1ReadingsCount + extraReadingsCount, smoothTemperatureReadings,
                    nullOutRedundantValues, removeRedundantIntermediateReadings);
        }

    }
}
