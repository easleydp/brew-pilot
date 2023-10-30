package com.easleydp.tempctrl.domain;

import static com.easleydp.tempctrl.util.StringUtils.humaniseUptime;
import static com.easleydp.tempctrl.util.StringUtils.prettyFormatUptime;

import java.lang.management.ManagementFactory;

import com.easleydp.tempctrl.util.StringUtils;

public class JvmStatus {

    @SuppressWarnings("unused")
    public final String uptime;

    @SuppressWarnings("unused")
    public final MemoryStats memory;

    public JvmStatus() {
        this.memory = new MemoryStatsJvm(Runtime.getRuntime());

        int uptimeMins = (int) (ManagementFactory.getRuntimeMXBean().getUptime() / (1000L * 60));
        this.uptime = humaniseUptime(prettyFormatUptime(uptimeMins));
    }

}