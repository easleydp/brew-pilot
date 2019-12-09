package com.easleydp.tempctrl.domain;

import static com.easleydp.tempctrl.domain.PropertyUtils.*;
import static com.easleydp.tempctrl.domain.optimise.RedundantValues.*;

import java.io.IOException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

import com.easleydp.tempctrl.domain.optimise.Smoother;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class Gyle extends GyleDto
{
    private static final Logger logger = LoggerFactory.getLogger(Gyle.class);

    private static Smoother smoother;
    private static int gen1ReadingsCount;
    private static boolean smoothTemperatureReadings;
    private static boolean nullOutRedundantValues;
    private static boolean removeRedundantIntermediateReadings;

    private Buffer buffer = null;


    public final Chamber chamber;
    public final Path gyleDir;
    public final Path logsDir;

    private LogAnalysis logAnalysis;

    public Gyle(Chamber chamber, Path gyleDir, Environment env)
    {
        this.chamber = chamber;
        this.gyleDir = gyleDir;
        this.logsDir = gyleDir.resolve("logs");

        if (smoother == null)
        {
            int thresholdHeight = getInteger(env, "readings.temp.smoothing.thresholdHeight", 2);
            int[] thresholdWidths = getIntArray(env, "readings.temp.smoothing.thresholdWidths", null);
            smoother = thresholdWidths == null ?
                    new Smoother(thresholdHeight) : new Smoother(thresholdHeight, thresholdWidths);

            gen1ReadingsCount = getInteger(env, "readings.gen1.readingsCount", 30);
            smoothTemperatureReadings = getBoolean(env, "readings.optimise.smoothTemperatureReadings", true);
            nullOutRedundantValues = getBoolean(env, "readings.optimise.nullOutRedundantValues", true);
            removeRedundantIntermediateReadings = getBoolean(env, "readings.optimise.removeRedundantIntermediate", true);
        }

        Path jsonFile = gyleDir.resolve("gyle.json");
        Assert.state(Files.exists(jsonFile), "gyle.json should exist");
        try
        {
            String json = FileUtils.readFileToString(jsonFile.toFile(), "UTF-8");
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
            buffer = new Buffer(timeNow);
        buffer.add(chamberReadings, timeNow);
        if (buffer.isReadyToBeFlushed())
        {
            buffer.flush(logsDir, logAnalysis);
            buffer = null;
            // TODO: maybeConsolidateLogFiles();
        }
    }

    private class LogAnalysis
    {
        final List<LogFileDescriptor> logFileDescriptors;

        LogAnalysis()
        {
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

                // Sort chronologically (latest last)
                Collections.sort(logFileDescriptors, new Comparator<LogFileDescriptor>() {
                    @Override
                    public int compare(LogFileDescriptor fd1, LogFileDescriptor fd2)
                    {
                        return fd1.dataBlockSeqNo - fd2.dataBlockSeqNo;
                    }
                });
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        LogFileDescriptor getLatestLogFileDescriptor()
        {
            return logFileDescriptors.isEmpty() ? null : logFileDescriptors.get(logFileDescriptors.size() - 1);
        }

        /**
         * Creates a LogFileDescriptor for the supplied log file (assumed to be the latest)
         * and adds it to our collection.
         */
        void addLogFileDescriptor(Path logFile)
        {
            logFileDescriptors.add(new LogFileDescriptor(logFile));
        }
    }

    private static final Pattern logFilePattern = Pattern.compile("^(\\d+)-(\\d+)-(\\d+)-(\\d+)\\.json$");

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
    }

    static class Buffer
    {
        private Date createdAt;
        private Date lastAddedAt;
        private List<ChamberReadings> readingsList = Collections.synchronizedList(new ArrayList<>());

        public Buffer(Date createdAt)
        {
            this.createdAt = createdAt;
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
            return readingsList.size() >= gen1ReadingsCount;
        }

        /** For Jackson */
        public List<ChamberReadings> getReadings()
        {
            return readingsList;
        }

        /**
         * Flush to disk file and possibly consolidate existing files.
         * Impl note: passing params rather than make the class non-static because Jackson needs
         * static class when deserialising.
         */
        public void flush(Path logsDir, LogAnalysis logAnalysis)
        {
            optimiseReadings();

            try
            {
                LogFileDescriptor latestLogFileDescriptor = logAnalysis.getLatestLogFileDescriptor();
                int dataBlockSeqNo = latestLogFileDescriptor == null ? 1 : latestLogFileDescriptor.dataBlockSeqNo + 1;
                String logFileName = dataBlockSeqNo + "-1-" + Utils.reduceUtcMillisPrecision(createdAt.getTime()) +
                        "-" + Utils.reduceUtcMillisPrecision(lastAddedAt.getTime()) + ".json";
                Path logFile = logsDir.resolve(logFileName);
                Files.createFile(logFile);
                logAnalysis.addLogFileDescriptor(logFile);

                ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
                mapper.writeValue(logFile.toFile(), this);

                // No need to clear `readings`; the caller will now release this buffer.
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        private void optimiseReadings()
        {
            // Must smooth before removing redundant records since the smoothing algorithm assumes readings
            // are taken with a fixed frequency.
            if (smoothTemperatureReadings)
                smoothTemperatureReadings();

            // For each ChamberReadings property:
            // If some contiguous readings have a property P with same value V then null-out
            // all the intermediate values (so they won't be serialised).
            if (nullOutRedundantValues)
            {
                for (String propertyName : ChamberReadings.getNullablePropertyNames())
                    nullOutRedundantValues(readingsList, propertyName);
            }

            // Given that the records have been fed through `nullOutRedundantReadings()`, any intermediate
            // records where all the nullable properties have been set to null are redundant.
            if (removeRedundantIntermediateReadings)
                removeRedundantIntermediateBeans(readingsList, ChamberReadings.getNullablePropertyNames());
        }

        /** Removes insignificant fluctuations in the temperature readings. */
        private void smoothTemperatureReadings()
        {
            smoother.smoothOutSmallFluctuations((List) readingsList, ChamberReadings.tTargetAccessor);
            smoother.smoothOutSmallFluctuations((List) readingsList, ChamberReadings.tBeerAccessor);
            smoother.smoothOutSmallFluctuations((List) readingsList, ChamberReadings.tExternalAccessor);
            smoother.smoothOutSmallFluctuations((List) readingsList, ChamberReadings.tChamberAccessor);
            smoother.smoothOutSmallFluctuations((List) readingsList, ChamberReadings.tPiAccessor);
        }

    }
}
