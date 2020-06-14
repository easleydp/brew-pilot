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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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
            new PiStats(),
            chamberManagerStatusSupplier.get()
        );
    }

    private static final class StatusReportResponse
    {
        @JsonInclude(Include.NON_NULL)
        public final PiStats raspberryPi;

        @JsonInclude(Include.NON_NULL)
        public final ChamberManagerStatus arduino;

        public StatusReportResponse(PiStats piStats, ChamberManagerStatus arduino)
        {
            this.raspberryPi = piStats;
            this.arduino = arduino;
        }

        @JsonInclude(Include.NON_NULL)
        public Boolean getArduinoIsOffline()
        {
            return arduino == null ? true : null;
        }
    }

    @JsonPropertyOrder({ "uptime", "temperature", "volts", "clock" })
    private static class PiStats
    {
        private static boolean mockPi = new File(vcgencmd).exists() == false;

        private final String uptime;

        @SuppressWarnings("unused")
        public final FileSystem fileSystem;

        @JsonInclude(Include.NON_NULL)
        public final JvmStatus jvm;

        public final String temperature;
        public final String volts;
        public final String clock;

        // Handy for testing
        public PiStats(String uptime, FileSystem fileSystem, JvmStatus jvm, String temperature, String volts, String clock)
        {
            this.uptime = uptime;
            this.fileSystem = fileSystem;
            this.temperature = temperature;
            this.jvm = jvm;
            this.volts = volts;
            this.clock = clock;
        }

        public PiStats()
        {

            this(
                OsCommandExecuter.execute("uptime", "-p"),
                new FileSystem(File.listRoots()[0]),
                new JvmStatus(),
                mockPi ? "temp=40.0'C" : OsCommandExecuter.execute(vcgencmd, "measure_temp"),
                mockPi ? "volt=0.8765V" : OsCommandExecuter.execute(vcgencmd, "measure_volts", "core"),
                mockPi ? "frequency(48)=750000000" : OsCommandExecuter.execute(vcgencmd, "measure_clock", "arm"));
        }

        @JsonInclude(Include.NON_NULL)
        public String getUptime()
        {
            // e.g. "up 19 hours, 31 minutes"
            return uptime != null ? substringAfter(uptime, "up ") : null;
        }

        @JsonInclude(Include.NON_NULL)
        public BigDecimal getTemperature()
        {
            if (temperature == null)
                return null;
            // e.g. "temp=41.0'C"
            return new BigDecimal(substringBetween(temperature, "=", "'"));
        }

        @JsonInclude(Include.NON_NULL)
        public BigDecimal getVolts()
        {
            if (volts == null)
                return null;
            // e.g. "volt=0.8563V"
            return new BigDecimal(substringBetween(volts, "=", "V"));
        }

        @JsonInclude(Include.NON_NULL)
        public Integer getClock()
        {
            if (clock == null)
                return null;
            // e.g. "frequency(48)=750199232"
            return Integer.parseInt(substringAfter(clock, "="), 10);
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
