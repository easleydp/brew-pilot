package com.easleydp.tempctrl.domain;

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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Chamber extends ChamberDto
{
    private final int id;
    private final Path chamberDir;
    private final List<Path> gyleDirs;  // In reverse order by ID
    private final Map<Integer, Path> gyleDirsById;  // In reverse order by ID
    private final Gyle activeGyle;
    private final Environment env;

    public Chamber(Path chamberDir, Environment env)
    {
        this.chamberDir = chamberDir;
        this.id = Integer.parseInt(chamberDir.getFileName().toString());
        this.env = env;

        Path jsonFile = chamberDir.resolve("chamber.json");
        Assert.state(Files.exists(jsonFile), "chamber.json should exist");

        try
        {
            String json = FileUtils.readFileToString(jsonFile.toFile(), StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            BeanUtils.copyProperties(mapper.readValue(json, ChamberDto.class), this);

            this.gyleDirs = getGyleDirs();
            this.gyleDirsById = new HashMap<>();
            for (Path gyleDir : gyleDirs)
                this.gyleDirsById.put(Integer.valueOf(gyleDir.getFileName().toString()), gyleDir);
            this.activeGyle = determineActiveGyleIfAny();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private Gyle determineActiveGyleIfAny()
    {
        return gyleDirs.stream()
            .map(gDir -> new Gyle(this, gDir, env))
            .filter(Gyle::isActive)
            .findFirst()
            .orElse(null);
    }

    /** Determine the gyle dirs, in reverse order (i.e. latest ID first). */
    private List<Path> getGyleDirs()
    {
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
                public int compare(Path p1, Path p2)
                {
                    int n1 = Integer.parseInt(p1.getFileName().toString());
                    int n2 = Integer.parseInt(p2.getFileName().toString());
                    return n2 - n1;
                }
            });

            return dirs;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public Gyle getGyleById(int id)
    {
        Path gyleDir = gyleDirsById.get(id);
        if (gyleDir == null)
            throw new IllegalArgumentException("No gyle exists with ID " + id);
        return new Gyle(this, gyleDir, env);
    }

    public List<Gyle> getGyles()
    {
        return gyleDirs.stream()
                .map(gDir -> new Gyle(this, gDir, env))
                .collect(Collectors.toList());
    }


    public int getId()
    {
        return id;
    }
    public Path getChamberDir()
    {
        return chamberDir;
    }
    public Gyle getActiveGyle()
    {
        return activeGyle;
    }

}
