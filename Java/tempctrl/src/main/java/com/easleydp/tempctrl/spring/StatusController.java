package com.easleydp.tempctrl.spring;

import static com.easleydp.tempctrl.util.StringUtils.substringAfter;
import static com.easleydp.tempctrl.util.StringUtils.substringBetween;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.easleydp.tempctrl.domain.ChamberManager;
import com.easleydp.tempctrl.domain.ChamberManagerStatus;
import com.easleydp.tempctrl.domain.JvmStatus;
import com.easleydp.tempctrl.domain.MemoryStatsFileSystem;
import com.easleydp.tempctrl.domain.MemoryStatsPi;
import com.easleydp.tempctrl.spring.CollectReadingsScheduler.ReadingsCollectionDurationStats;
import com.easleydp.tempctrl.util.OsCommandExecuter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

@RestController
public class StatusController {
    private static final Logger logger = LoggerFactory.getLogger(StatusController.class);

    @Autowired
    private ChamberManager chamberManager;

    @Autowired
    private CollectReadingsScheduler collectReadingsScheduler;

    @Autowired
    private OfflineCheckScheduler offlineCheckScheduler;

    Supplier<ChamberManagerStatus> chamberManagerStatusSupplier;

    public StatusController() {
        chamberManagerStatusSupplier = Suppliers.memoizeWithExpiration(
                new Supplier<ChamberManagerStatus>() {
                    @Override
                    public ChamberManagerStatus get() {
                        try {
                            return chamberManager.getChamberManagerStatus();
                        } catch (IOException e) {
                            logger.error(e.getMessage(), e);
                            return null;
                        }
                    }
                }, 10, TimeUnit.SECONDS);
    }

    private static final String VCGEN_CMD = "/opt/vc/bin/vcgencmd";

    @GetMapping("/guest/log-chart/status")
    public StatusReportResponse getStatusReport() throws IOException {
        return buildStatusReportResponse();
    }

    // Called by getStatusReport() above and also by StillAliveMessageScheduler
    StatusReportResponse buildStatusReportResponse() {
        List<Date> recentlyOfflineDates = offlineCheckScheduler.getRecentlyOffline(new Date());
        // @formatter:off
        List<String> recentlyOfflineIso = recentlyOfflineDates.stream()
            .map(d -> dateToIsoUtc(d))
            .collect(Collectors.toList());
        // @formatter:on

        return new StatusReportResponse(
                new PiStats(),
                chamberManagerStatusSupplier.get(),
                collectReadingsScheduler.getReadingsCollectionDurationStats(),
                recentlyOfflineIso);
    }

    private static String dateToIsoUtc(Date date) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(tz);
        return df.format(date);
    }

    @JsonPropertyOrder({ "garageTemperature", "projectBoxTemperature" })
    private static final class StatusReportResponse {
        public BigDecimal getGarageTemperature() {
            return arduino != null ? arduino.getGarageTemperature() : null;
        }

        public BigDecimal getProjectBoxTemperature() {
            return arduino != null ? arduino.getProjectBoxTemperature() : null;
        }

        public final PiStats raspberryPi;
        @JsonInclude(Include.NON_NULL)
        public final ChamberManagerStatus arduino;
        @JsonInclude(Include.NON_NULL)
        public final ReadingsCollectionDurationStats readingsCollectionDurationStats;
        @JsonInclude(Include.NON_EMPTY)
        public final List<String> recentlyOffline;

        public StatusReportResponse(PiStats piStats, ChamberManagerStatus arduino,
                ReadingsCollectionDurationStats readingsCollectionDurationStats, List<String> recentlyOffline) {
            this.raspberryPi = piStats;
            this.arduino = arduino;
            this.readingsCollectionDurationStats = readingsCollectionDurationStats;
            this.recentlyOffline = recentlyOffline;
        }

        @JsonInclude(Include.NON_NULL)
        public Boolean getArduinoIsOffline() {
            return arduino == null ? true : null;
        }
    }

    @JsonPropertyOrder({ "uptime", "socTemperature", "cpuTemperature", "clockMHz", "localIP", "macAddress" })
    private static class PiStats {
        private static boolean mockPi = new File(VCGEN_CMD).exists() == false;

        private final String uptime;

        @SuppressWarnings("unused")
        public final MemoryStatsFileSystem fileSystem;
        @SuppressWarnings("unused")
        public final MemoryStatsPi memory;

        @JsonInclude(Include.NON_NULL)
        public final JvmStatus jvm;

        private final String socTemperature;
        private final String cpuTemperature;
        private final String volts;
        private final String clock;

        // Handy for testing
        public PiStats(String uptime, MemoryStatsPi memory, MemoryStatsFileSystem fileSystem, JvmStatus jvm,
                String socTemperature, String cpuTemperature, String volts, String clock) {
            this.uptime = uptime;
            this.memory = memory;
            this.fileSystem = fileSystem;
            this.socTemperature = socTemperature;
            this.cpuTemperature = cpuTemperature;
            this.jvm = jvm;
            this.volts = volts;
            this.clock = clock;
        }

        public PiStats() {
            this(
                    OsCommandExecuter.execute("uptime", "-p"),
                    new MemoryStatsPi(),
                    new MemoryStatsFileSystem(File.listRoots()[0]),
                    new JvmStatus(),
                    mockPi ? "temp=30.2'C" : OsCommandExecuter.execute(VCGEN_CMD, "measure_temp"),
                    mockPi ? "30280" : OsCommandExecuter.execute("cat", "/sys/class/thermal/thermal_zone0/temp"),
                    mockPi ? "volt=0.8765V" : OsCommandExecuter.execute(VCGEN_CMD, "measure_volts", "core"),
                    mockPi ? "frequency(48)=750199232" : OsCommandExecuter.execute(VCGEN_CMD, "measure_clock", "arm"));
        }

        @JsonInclude(Include.NON_NULL)
        public String getUptime() {
            // e.g. "up 19 hours, 31 minutes"
            return uptime != null ? substringAfter(uptime, "up ") : null;
        }

        @JsonInclude(Include.NON_NULL)
        public BigDecimal getSocTemperature() {
            if (socTemperature == null)
                return null;
            // e.g. "temp=41.0'C"
            return new BigDecimal(substringBetween(socTemperature, "=", "'"));
        }

        // @formatter:off
        // Seems this is never significantly different from socTemperature, so omit for now.
        // @JsonInclude(Include.NON_NULL)
        // public BigDecimal getCpuTemperature()
        // {
        //     if (cpuTemperature == null)
        //         return null;
        //     // e.g. "30180"
        //     return new BigDecimal(cpuTemperature)
        //         .divide(new BigDecimal(1000))
        //         .setScale(1, java.math.RoundingMode.HALF_UP);
        // }

        // Who cares about this value (particularly since it seems to bear no relation to what we understand as the Pi's input voltage)?
        // @JsonInclude(Include.NON_NULL)
        // public BigDecimal getVoltage()
        // {
        //     if (volts == null)
        //         return null;
        //     // e.g. "volt=0.8563V"
        //     return new BigDecimal(substringBetween(volts, "=", "V"));
        // }
        // @formatter:on

        @JsonInclude(Include.NON_NULL)
        public BigDecimal getClockMHz() {
            if (clock == null)
                return null;
            // e.g. "frequency(48)=750199232"
            return BigDecimal
                    .valueOf(((double) Integer.parseInt(substringAfter(clock, "="), 10)) / 1_000_000)
                    .setScale(1, java.math.RoundingMode.HALF_UP);
        }

        @JsonInclude(Include.NON_NULL)
        public String getLocalIP() {
            // Credit: https://stackoverflow.com/a/38342964/65555
            try (final DatagramSocket socket = new DatagramSocket()) {
                socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                return socket.getLocalAddress().getHostAddress();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                return null;
            }
        }

        @JsonInclude(Include.NON_NULL)
        public String getMacAddress() {
            // Credit: https://stackoverflow.com/a/26667426/65555
            try {
                Enumeration<NetworkInterface> netInfs = NetworkInterface.getNetworkInterfaces();
                if (!netInfs.hasMoreElements()) {
                    logger.warn("NetworkInterface.getNetworkInterfaces() returned no interfaces!");
                    return null;
                }
                final NetworkInterface netInf = netInfs.nextElement();
                final byte[] mac = netInf.getHardwareAddress();
                final StringBuilder sb = new StringBuilder();
                for (int i = 0; i < mac.length; i++) {
                    sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
                }
                return sb.toString();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                return null;
            }
        }
    }

}
