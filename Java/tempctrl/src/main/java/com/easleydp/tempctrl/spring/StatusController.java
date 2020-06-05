package com.easleydp.tempctrl.spring;

import static com.easleydp.tempctrl.util.StringUtils.substringAfter;
import static com.easleydp.tempctrl.util.StringUtils.substringBetween;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import com.easleydp.tempctrl.domain.ChamberManager;
import com.easleydp.tempctrl.domain.ChamberManagerStatus;
import com.easleydp.tempctrl.domain.JvmStatus;
import com.easleydp.tempctrl.util.OsCommandExecuter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController
{
    @Autowired
    private ChamberManager chamberManager;

    Supplier<ChamberManagerStatus> chamberManagerStatusSupplier;

    public StatusController()
    {
        chamberManagerStatusSupplier = Suppliers.memoizeWithExpiration(
                new Supplier<ChamberManagerStatus>() {
                    @Override
                    public ChamberManagerStatus get() {
                        try
                        {
                            return chamberManager.getChamberManagerStatus();
                        }
                        catch (IOException e)
                        {
                            return null;
                        }
                    }
                  }, 10, TimeUnit.SECONDS);
    }

    private static final String vcgencmd = "/opt/vc/bin/vcgencmd";

    @GetMapping("/guest/log-chart/status")
    public StatusReportResponse getStatusReport() throws IOException
    {
        return new StatusReportResponse(
                OsCommandExecuter.execute("uptime", "-p"),
                new File(vcgencmd).exists() ? new PiStats() : null,
                new JvmStatus(),
                new FileSystem(File.listRoots()[0]),
                chamberManagerStatusSupplier.get()
        );
    }

    private static final class StatusReportResponse
    {
        private final String uptime;

        private final PiStats piStats;

        @SuppressWarnings("unused")
        public final FileSystem fileSystem;

        @JsonInclude(Include.NON_NULL)
        public final JvmStatus jvm;

        @JsonInclude(Include.NON_NULL)
        public final ChamberManagerStatus arduino;

        public StatusReportResponse(String uptime, PiStats piStats, JvmStatus jvm, FileSystem fileSystem, ChamberManagerStatus arduino)
        {
            this.uptime = uptime;
            this.piStats = piStats;
            this.jvm = jvm;
            this.fileSystem = fileSystem;
            this.arduino = arduino;
        }

        @JsonInclude(Include.NON_NULL)
        public String getUptime()
        {
            // e.g. "up 19 hours, 31 minutes"
            return uptime != null ? substringAfter(uptime, "up ") : null;
        }

        @JsonInclude(Include.NON_NULL)
        public BigDecimal getPiTemperature()
        {
            if (piStats == null || piStats.temperature == null)
                return null;
            // e.g. "temp=41.0'C"
            return new BigDecimal(substringBetween(piStats.temperature, "=", "'"));
        }

        @JsonInclude(Include.NON_NULL)
        public BigDecimal getPiVolts()
        {
            if (piStats == null || piStats.volts == null)
                return null;
            // e.g. "volt=0.8563V"
            return new BigDecimal(substringBetween(piStats.volts, "=", "V"));
        }

        @JsonInclude(Include.NON_NULL)
        public Integer getPiClock()
        {
            if (piStats == null || piStats.clock == null)
                return null;
            // e.g. "frequency(48)=750199232"
            return Integer.parseInt(substringAfter(piStats.clock, "="), 10);
        }

        @JsonInclude(Include.NON_NULL)
        public Boolean getArduinoIsOffline()
        {
            return arduino == null ? true : null;
        }
    }

    private static class PiStats
    {
        public final String temperature;
        public final String volts;
        public final String clock;

        // Handy for testing
        public PiStats(String temperature, String volts, String clock)
        {
            this.temperature = temperature;
            this.volts = volts;
            this.clock = clock;
        }

        public PiStats()
        {
            this(
                OsCommandExecuter.execute(vcgencmd, "measure_temp"),
                OsCommandExecuter.execute(vcgencmd, "measure_volts", "core"),
                OsCommandExecuter.execute(vcgencmd, "measure_clock", "arm"));
        }
    }

    private static class FileSystem
    {
        public final long total;  // Size of the partition. (This is the largest of three figures.)
        public final long free;   // Number of unallocated bytes in the partition. (Less than total, greater than usable.)
        // public final long usable; // Space available to this virtual machine. (This is the lowest of three figures.)

        @SuppressWarnings("unused")
        public int getPercentageFree()
        {
            return (int)(free * 100 / total);
        }

        FileSystem(File root)
        {
            total = root.getTotalSpace();
            free = root.getFreeSpace();
            // usable = root.getUsableSpace();
        }
    }

}
