package com.easleydp.tempctrl.domain;

abstract class MemoryStats {
    public final long total;
    public final long free;

    MemoryStats(long total, long free) {
        if (total <= 0) {
            throw new IllegalArgumentException("total is less than or equal to zero: " + total);
        }
        if (total < free) {
            throw new IllegalArgumentException("total is less than free: " + total + ", " + free);
        }
        this.total = total;
        this.free = free;
    }


    @SuppressWarnings("unused")
    public int getPercentageFree()
    {
        return Math.round(free * 100F / total);
    }
}
