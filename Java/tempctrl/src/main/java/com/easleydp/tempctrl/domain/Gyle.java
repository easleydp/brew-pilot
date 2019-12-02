package com.easleydp.tempctrl.domain;

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
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class Gyle extends GyleDto
{
    private static final Logger logger = LoggerFactory.getLogger(Gyle.class);

    private Buffer buffer = null;


    public final Chamber chamber;
    public final Path gyleDir;
    public final Path logsDir;

    private LogAnalysis logAnalysis;

    public Gyle(Chamber chamber, Path gyleDir)
    {
        this.chamber = chamber;
        this.gyleDir = gyleDir;
        this.logsDir = gyleDir.resolve("logs");

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

        ChamberReadings chamberReadings = chamberManager.getReadings(chamber.getId());

        if (logAnalysis == null)
            logAnalysis = new LogAnalysis();  // Fail fast rather than leave this until first flush

        // Ensure we have a buffer and write the readings to it.
        // If the memory buffer is ready to be flushed, flush and delete. (Note, flushing should also
        // trigger file consolidation if necessary.)
        if (buffer == null)
            buffer = new Buffer(timeNow);
        buffer.add(chamberReadings, timeNow);
        if (buffer.isReadyToBeFlushed(timeNow))
        {
            buffer.flush();
            buffer = null;
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
    }

    private static final Pattern logFilePattern = Pattern.compile("^(\\d+)-(\\d+)-(\\d+)-(\\d+)\\.json$");

    private static class LogFileDescriptor
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

    }

    private class Buffer
    {
        private final Date createdAt;
        private Date lastAddedAt;
        private List<ChamberReadings> readings = new ArrayList<>();

        public Buffer(Date createdAt)
        {
            this.createdAt = createdAt;
        }

        public void add(ChamberReadings chamberReadings, Date addedAt)
        {
            readings.add(chamberReadings);
            lastAddedAt = addedAt;
        }

        public boolean isReadyToBeFlushed(Date timeNow)
        {
            long diffMinutes = (timeNow.getTime() - createdAt.getTime()) / (1000 * 60);
            return diffMinutes >= 10;  // TODO: Make configurable
        }

        /** For Jackson */
        @SuppressWarnings("unused")
        public List<ChamberReadings> getReadings()
        {
            return readings;
        }

        /** Flush to disk file and possibly consolidate existing files. */
        public void flush()
        {
            try
            {
                LogFileDescriptor latestLogFileDescriptor = logAnalysis.getLatestLogFileDescriptor();
                int dataBlockSeqNo = latestLogFileDescriptor == null ? 1 : latestLogFileDescriptor.dataBlockSeqNo + 1;
                String logFileName = dataBlockSeqNo + "-1-" + Utils.reduceUtcMillisPrecision(createdAt.getTime()) +
                        "-" + Utils.reduceUtcMillisPrecision(lastAddedAt.getTime()) + ".json";
                Path logFile = logsDir.resolve(logFileName);
                    Files.createFile(logFile);

                ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
                mapper.writeValue(logFile.toFile(), this);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

    }
}
