package com.easleydp.tempctrl.domain;

class MemoryStatsJvm extends MemoryStats {
    MemoryStatsJvm(Runtime runtime) {
        super(runtime.totalMemory(), runtime.freeMemory());
    }
}
