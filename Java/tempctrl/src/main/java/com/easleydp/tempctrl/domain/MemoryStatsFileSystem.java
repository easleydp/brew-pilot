package com.easleydp.tempctrl.domain;

import java.io.File;

public class MemoryStatsFileSystem extends MemoryStats {
    public MemoryStatsFileSystem(File root) {
        super(root.getTotalSpace(), root.getFreeSpace());
    }
}
