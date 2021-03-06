package com.easleydp.tempctrl.domain;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.util.Assert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NOTE: This is a stateful bean since it wraps a 'latest Gyle' which is itself
 * stateful (see Gyle.java).
 */
public class Chamber extends ChamberDto {
    private static final Logger logger = LoggerFactory.getLogger(Chamber.class);

    private final int id;
    private final Path chamberDir;
    private List<Path> gyleDirs; // In reverse order by ID
    private Map<Integer, Path> gyleDirsById; // In reverse order by ID
    private Gyle latestGyle;
    private Path jsonFile;
    private Long jsonFileLastModified;

    public Chamber(Path chamberDir) {
        this.chamberDir = chamberDir;
        this.id = Integer.parseInt(chamberDir.getFileName().toString());

        this.jsonFile = chamberDir.resolve("chamber.json");
        checkForUpdates();
    }

    private synchronized void digestGyleDirs() {
        this.gyleDirs = getGyleDirs();
        this.gyleDirsById = new HashMap<>();
        for (Path gyleDir : gyleDirs)
            this.gyleDirsById.put(Integer.valueOf(gyleDir.getFileName().toString()), gyleDir);
    }

    /**
     * Checked to see if the underlying JSON file rep has been updated and, if so,
     * refreshes this bean's properties. Also checks whether the 'latest gyle' has
     * been updated or superseded.
     */
    public synchronized void checkForUpdates() {
        Assert.state(Files.exists(jsonFile), "chamber.json should exist");

        try {
            File file = jsonFile.toFile();
            if (jsonFileLastModified == null || jsonFileLastModified < file.lastModified()) {
                if (jsonFileLastModified != null)
                    logger.info("chamber.json updated, id={}", id);
                String json = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                ObjectMapper mapper = new ObjectMapper();
                BeanUtils.copyProperties(mapper.readValue(json, ChamberDto.class), this);
                jsonFileLastModified = file.lastModified();
            }

            checkForGyleUpdates();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Checks whether the 'latest gyle' has been updated or superseded. If updated,
     * it is refreshed, i.e. properties from the JSON file are re-read into existing
     * bean. If superseded, the current `latestGyle` bean is closed (readings
     * flushed) and a replacement is created.
     */
    private synchronized void checkForGyleUpdates() {
        digestGyleDirs();
        Gyle _latestGyle = determineLatestGyleIfAny();
        if (_latestGyle == null || latestGyle == null) {
            latestGyle = _latestGyle;
        } else if (_latestGyle.getId() != latestGyle.getId()) {
            logger.info("**** Chamber " + id + "'s latest gyle (" + latestGyle.getId() + ") superseded: now "
                    + _latestGyle.getId());
            latestGyle.close();
            latestGyle = _latestGyle;
        } else if (_latestGyle.getFileLastModified() > latestGyle.getFileLastModified()) {
            logger.info("**** Chamber " + id + "'s latest gyle (" + latestGyle.getId() + ") updated.");
            latestGyle.refresh();
        }
    }

    private synchronized Gyle determineLatestGyleIfAny() {
        // gyleDirs is sorted by id desc
        Path latestGyleDir = gyleDirs.isEmpty() ? null : gyleDirs.get(0);
        return latestGyleDir != null ? new Gyle(this, latestGyleDir) : null;
    }

    /**
     * Determine the gyle dirs, in reverse order (i.e. latest ID first). Ignores
     * dirs with no "gyle.json" file
     */
    private synchronized List<Path> getGyleDirs() {
        Path gylesDir = chamberDir.resolve("gyles");
        Assert.state(Files.exists(gylesDir), "'gyles' dir should exist for chamber " + id);

        try (Stream<Path> stream = Files.walk(gylesDir, 1)) {
            List<Path> dirs = stream
                    .filter(path -> Files.isDirectory(path))
                    .filter(path -> StringUtils.isNumeric(path.getFileName().toString()))
                    .filter(path -> Files.exists(path.resolve("gyle.json")))
                    .collect(Collectors.toList());

            Collections.sort(dirs, new Comparator<Path>() {
                @Override
                public int compare(Path p1, Path p2) {
                    int n1 = Integer.parseInt(p1.getFileName().toString());
                    int n2 = Integer.parseInt(p2.getFileName().toString());
                    return n2 - n1;
                }
            });

            return dirs;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized Gyle getGyleById(int id) {
        Path gyleDir = gyleDirsById.get(id);
        if (gyleDir == null)
            throw new IllegalArgumentException("No gyle exists with ID " + id);
        return new Gyle(this, gyleDir);
    }

    public synchronized List<Gyle> getGyles() {
        return gyleDirs.stream()
                .map(gDir -> new Gyle(this, gDir))
                .collect(Collectors.toList());
    }

    public int getId() {
        return id;
    }

    public Path getChamberDir() {
        return chamberDir;
    }

    public synchronized Gyle getLatestGyle() {
        return latestGyle;
    }

    /**
     * Returns ChamberParameters sans gyleAgeHours, tTarget, tTargetNext and mode.
     * Serves as a sub for Gyle.getChamberParameters() when there is no
     * active/latest gyle.
     */
    public ChamberParameters getPartialChamberParameters() {
        return new ChamberParameters(0, 0, 0,
                this.gettMin(), this.gettMax(), this.isHasHeater(),
                this.getFridgeMinOnTimeMins(), this.getFridgeMinOffTimeMins(), this.getFridgeSwitchOnLagMins(),
                this.getKp(), this.getKi(), this.getKd(), Mode.MONITOR_ONLY);
    }

}
