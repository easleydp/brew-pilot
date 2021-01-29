package com.easleydp.tempctrl.domain;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

// Don't know why JsonPropertyOrder needed here but without it "percentageFree" doesn't come last
@JsonPropertyOrder({ "totalBytes", "totalKb", "totalMb", "totalGb", "freeKb", "freeMb", "freeGb", "percentageFree" })
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class MemoryStats {
    private final long total;
    private final long free;

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


    /*
     * For `total` and `free` we provide a four alternate getters, each having a distinct property name:
     * one returning integer bytes or null, one returning decimal KB or null, one returning decimal MB or null, one returning decimal GB or null.
     * One and only one will return a non-null value. Null values won't be included in JSON.
     */

    private static final long K_BYTE_COUNT = 1024;
    private static final long M_BYTE_COUNT = K_BYTE_COUNT * K_BYTE_COUNT;
    private static final long G_BYTE_COUNT = K_BYTE_COUNT * M_BYTE_COUNT;

    // Helpers
    private Integer getBytes(long value) {
        return value < K_BYTE_COUNT ? (int)value : null;
    }
    private BigDecimal getKb(long value) {
        return value >= K_BYTE_COUNT && value < M_BYTE_COUNT ? BigDecimal.valueOf(((double) value) / K_BYTE_COUNT)  : null;
    }
    private BigDecimal getMb(long value) {
        return value >= M_BYTE_COUNT && value < G_BYTE_COUNT ? BigDecimal.valueOf(((double) value) / M_BYTE_COUNT)  : null;
    }
    private BigDecimal getGb(long value) {
        return value >= G_BYTE_COUNT ? BigDecimal.valueOf(((double) value) / G_BYTE_COUNT)  : null;
    }

    /* getters for `total`, one and only one of which will return non-null */
    public Integer getTotalBytes() {
        return getBytes(total);
    }
    public BigDecimal getTotalKb() {
        return getKb(total);
    }
    public BigDecimal getTotalMb() {
        return getMb(total);
    }
    public BigDecimal getTotalGb() {
        return getGb(total);
    }

    /* getters for `free`, one and only one of which will return non-null */
    public Integer getFreeBytes() {
        return getBytes(free);
    }
    public BigDecimal getFreeKb() {
        return getKb(free);
    }
    public BigDecimal getFreeMb() {
        return getMb(free);
    }
    public BigDecimal getFreeGb() {
        return getGb(free);
    }




    public int getPercentageFree()
    {
        return Math.round(free * 100F / total);
    }

}
