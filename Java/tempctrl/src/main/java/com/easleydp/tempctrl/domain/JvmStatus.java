package com.easleydp.tempctrl.domain;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

import com.easleydp.tempctrl.util.StringUtils;

public class JvmStatus {

    @SuppressWarnings("unused")
    public final String uptime;

    @SuppressWarnings("unused")
    public final Memory memory;

    private static class Memory
    {
        public final long total;
        public final long free;


        @SuppressWarnings("unused")
        public int getPercentageFree()
        {
            return (int)(free * 100 / total);
        }

        Memory()
        {
            free = Runtime.getRuntime().freeMemory();
            total = Runtime.getRuntime().totalMemory();
        }
    }

    public JvmStatus() {
        this.memory = new Memory();

        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        int uptimeMins = (int) (rb.getUptime() / (1000L * 60));
        this.uptime = StringUtils.friendlyUptime(uptimeMins);
    }

}