package com.easleydp.tempctrl.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

// Unclear why `@JsonPropertyOrder` is needed here but without it "percentageFree" doesn't come last
@JsonPropertyOrder({ "totalBytes", "totalKB", "totalMB", "totalGB", "freeKB", "freeMB", "freeGB", "percentageFree" })
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
     * For the `total` property we provide a four different getters, each having a distinct property name;
     * the first returns integer bytes or null; the second returns decimal KB or null; the third returns
     * decimal MB or null; the fourth returns decimal GB or null. One and only one will return a non-null
     * value. Null values won't be included in JSON.
     * Likewise for the `free` property.
     */

    private static final long K_BYTE_COUNT = 1024;
    private static final long M_BYTE_COUNT = K_BYTE_COUNT * K_BYTE_COUNT;
    private static final long G_BYTE_COUNT = K_BYTE_COUNT * M_BYTE_COUNT;

    // Helpers
    private static Integer getBytes(long value) {
        return value < K_BYTE_COUNT ? (int)value : null;
    }
    private static BigDecimal getKb(long value) {
        return value < M_BYTE_COUNT && value >= K_BYTE_COUNT ? divide(value, K_BYTE_COUNT) : null;
    }
    private static BigDecimal getMb(long value) {
        return value < G_BYTE_COUNT && value >= M_BYTE_COUNT ? divide(value, M_BYTE_COUNT) : null;
    }
    private static BigDecimal getGb(long value) {
        return value >= G_BYTE_COUNT ? divide(value, G_BYTE_COUNT) : null;
    }
    private static BigDecimal divide(long value, long divisor) {
        return BigDecimal.valueOf(((double) value) / divisor).setScale(1, RoundingMode.HALF_UP);
    }

    /* getters for `total`, one and only one of which will return non-null */
    public Integer getTotalBytes() {
        return getBytes(total);
    }
    public BigDecimal getTotalKB() {
        return getKb(total);
    }
    public BigDecimal getTotalMB() {
        return getMb(total);
    }
    public BigDecimal getTotalGB() {
        return getGb(total);
    }

    /* getters for `free`, one and only one of which will return non-null */
    public Integer getFreeBytes() {
        return getBytes(free);
    }
    public BigDecimal getFreeKB() {
        return getKb(free);
    }
    public BigDecimal getFreeMB() {
        return getMb(free);
    }
    public BigDecimal getFreeGB() {
        return getGb(free);
    }




    public int getPercentageFree()
    {
        return Math.round(free * 100F / total);
    }

}
