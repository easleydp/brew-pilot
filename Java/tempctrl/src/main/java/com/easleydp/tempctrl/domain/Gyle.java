package com.easleydp.tempctrl.domain;

import static com.easleydp.tempctrl.domain.Gyle.LogFileDescriptor.*;
import static com.easleydp.tempctrl.domain.PropertyUtils.*;
import static com.easleydp.tempctrl.domain.Utils.*;
import static com.easleydp.tempctrl.domain.optimise.RedundantValues.*;

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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

import com.easleydp.tempctrl.domain.optimise.Smoother;
import com.easleydp.tempctrl.domain.optimise.Smoother.IntPropertyAccessor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SequenceWriter;

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


    public final Chamber chamber;
    public final Path gyleDir;
    public final Path logsDir;

    private final Environment env;
    private LogAnalysis logAnalysis;

    public Gyle(Chamber chamber, Path gyleDir, Environment env)
    {
        this.chamber = chamber;
        this.gyleDir = gyleDir;
        this.logsDir = gyleDir.resolve("logs");
        this.env = env;

        int thresholdHeight = getInteger(env, "readings.temp.smoothing.thresholdHeight", 2);
        int[] thresholdWidths = getIntArray(env, "readings.temp.smoothing.thresholdWidths", null);
        smoother = thresholdWidths == null ?
                new Smoother(thresholdHeight) : new Smoother(thresholdHeight, thresholdWidths);

        bufferConfig = new BufferConfig(
                getInteger(env, "readings.gen1.readingsCount", 30),
                getBoolean(env, "readings.optimise.smoothTemperatureReadings", true),
                getBoolean(env, "readings.optimise.nullOutRedundantValues", true),
                getBoolean(env, "readings.optimise.removeRedundantIntermediate", true));

        Path jsonFile = gyleDir.resolve("gyle.json");
        Assert.state(Files.exists(jsonFile), "gyle.json should exist");
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
     */
    public void collectReadings(ChamberManager chamberManager, Date timeNow)
    {
        logger.debug("collectReadings()");

        ChamberReadings chamberReadings = chamberManager.getReadings(chamber.getId(), timeNow);

        if (logAnalysis == null)
            logAnalysis = new LogAnalysis();  // Fail fast rather than leave this until first flush

        // Ensure we have a buffer and write the readings to it.
        // If the memory buffer is ready to be flushed, flush and delete. (Note, flushing should also
        // trigger file consolidation if necessary.)
        if (buffer == null)
            buffer = new Buffer(timeNow, bufferConfig, smoother);
        buffer.add(chamberReadings, timeNow);
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

    private class LogAnalysis
    {
        final int genMultiplier;
        final int maxGenerations;
        final List<LogFileDescriptor> logFileDescriptors;
        private int nextDataBlockSeqNo;
        private List<LogFileDescriptor> awaitingCleanup = new ArrayList<>();

        LogAnalysis()
        {
            genMultiplier = PropertyUtils.getInteger(env, "readings.gen.multiplier", 10);
            maxGenerations = PropertyUtils.getInteger(env, "readings.gen.max", 4);
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
                    logger.warn(logFileDescriptors.size() + " gyle log file(s) found on start-up in " + logsDir);

                    // Sort chronologically
                    Collections.sort(logFileDescriptors, new Comparator<LogFileDescriptor>() {
                        @Override
                        public int compare(LogFileDescriptor fd1, LogFileDescriptor fd2)
                        {
                            return fd1.dataBlockSeqNo - fd2.dataBlockSeqNo;
                        }
                    });

                    LogFileDescriptor latest = logFileDescriptors.get(logFileDescriptors.size() - 1);
                    // Leave a gap to highlight the discontinuity.
                    nextDataBlockSeqNo = latest.dataBlockSeqNo + 10;
                }
                else
                {
                    nextDataBlockSeqNo = 1;
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        int getNextDataBlockSeqNo()
        {
            return nextDataBlockSeqNo++;
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
            LogFileDescriptor first = genNDescriptors.get(0);
            LogFileDescriptor last = genNDescriptors.get(genNDescriptors.size() - 1);
            Path newLogFile = logsDir.resolve(buildLogFilename(first.dataBlockSeqNo, gen, first.dtStart, last.dtEnd));

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

    private static final Pattern logFilePattern = Pattern.compile("^(\\d+)-(\\d+)-(\\d+)-(\\d+)\\.ndjson$");

    static class LogFileDescriptor
    {
        final Path logFile;
        final int dataBlockSeqNo;
        final int generation;
        final int dtStart;
        final int dtEnd;

        public LogFileDescriptor(Path logFile)
        {
            this.logFile = logFile;

            String logfileName = logFile.getFileName().toString();
            Matcher matcher = logFilePattern.matcher(logfileName);
            Assert.state(matcher.matches(), "Invalid log file name: " + logfileName);

            this.dataBlockSeqNo = Integer.parseInt(matcher.group(1), 10);
            this.generation = Integer.parseInt(matcher.group(2), 10);
            this.dtStart = Integer.parseInt(matcher.group(3), 10);
            this.dtEnd = Integer.parseInt(matcher.group(4), 10);
        }

        public String getFilename()
        {
            return logFile.getFileName().toString();
        }

        public static final String sep = "-";
        public static String buildLogFilename(int dataBlockSeqNo, int generation, int dtStart, int dtEnd)
        {
            return dataBlockSeqNo + sep + generation + sep + dtStart + sep + dtEnd + ".ndjson";
        }
        public static String buildLogFilename(int dataBlockSeqNo, int generation, Date dtStart, Date dtEnd)
        {
            return buildLogFilename(dataBlockSeqNo, generation,
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
                String logFileName = buildLogFilename(logAnalysis.getNextDataBlockSeqNo(), 1, createdAt, lastAddedAt);
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
            // all the intermediate values (so they won't be serialised).
            if (config.nullOutRedundantValues)
                for (String propertyName : ChamberReadings.getNullablePropertyNames())
                    nullOutRedundantValues(readingsList, propertyName);

            // Given that the records have been fed through `nullOutRedundantReadings()`, any intermediate
            // records where all the nullable properties have been set to null are redundant.
            if (config.removeRedundantIntermediateReadings)
                removeRedundantIntermediateBeans(readingsList, ChamberReadings.getNullablePropertyNames());
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

    }
}
