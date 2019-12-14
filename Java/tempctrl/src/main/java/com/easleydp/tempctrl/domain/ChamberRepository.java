package com.easleydp.tempctrl.domain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

/**
 * Produces and collects Chamber instances.
 */
public class ChamberRepository
{
    private final Path chambersDir;

    /** Latest first */
    private final Queue<Chamber> chambers = new ConcurrentLinkedQueue<>();

    public ChamberRepository(Path dataDir, Environment env)
    {
        Assert.state(Files.exists(dataDir), "data dir should exist");
        chambersDir = dataDir.resolve("chambers");
        Assert.state(Files.exists(chambersDir), "chambers dir should exist");

        // Every chamber is configured in a numbered sub-dir "1", "2", etc.
        // First step is to get such dirs in (reverse) order (2, 1).
        List<Path> chamberDirs = getChamberDirs();

        // Now we can create a correctly order list of Chambers.
        chamberDirs.stream()
            .map(cd -> new Chamber(cd, env))
            .forEach(c -> {
                chambers.add(c);
            });
    }

    /** Finds chamber dirs in reverse order (i.e. latest ID first). */
    private List<Path> getChamberDirs()
    {
        try (Stream<Path> stream = Files.walk(chambersDir, 1)) {
            List<Path> dirs = stream
                    .filter(path -> Files.isDirectory(path))
                    .filter(path -> StringUtils.isNumeric(path.getFileName().toString()))
                    .filter(path -> Files.exists(path.resolve("chamber.json")))
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

    public Collection<Chamber> getChambers()
    {
        return chambers;
    }

    public Chamber getChamberById(int id)
    {
        return chambers.stream()
                .filter(ch -> ch.getId() == id)
                .findFirst()
                .orElseThrow();
    }
}
