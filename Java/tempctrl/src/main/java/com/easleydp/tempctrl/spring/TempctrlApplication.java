package com.easleydp.tempctrl.spring;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.TimeZone;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.Assert;

import com.easleydp.tempctrl.domain.ArduinoChamberManager;
import com.easleydp.tempctrl.domain.ChamberManager;
import com.easleydp.tempctrl.domain.ChamberRepository;
import com.easleydp.tempctrl.domain.DummyChamberManager;
import com.easleydp.tempctrl.domain.PropertyUtils;

@SpringBootApplication(
// Uncomment this line to temporarily disable Spring Security:
// exclude = { SecurityAutoConfiguration.class,
// ManagementWebSecurityAutoConfiguration.class }
)
@EnableScheduling
@EnableAsync
@EnableRetry
public class TempctrlApplication {
    private static final Logger logger = LoggerFactory.getLogger(TempctrlApplication.class);

    // (This static block needs to be placed in any class which is loaded at
    // startup).
    static {
        ensureAppTimeZoneIsUtc();
    }

    @Autowired
    private Environment env;

    @PostConstruct
    public void init() {
        PropertyUtils.setEnv(env);
        logger.debug("Current working dir is {}", System.getProperty("user.dir"));
    }

    @Bean
    public Path dataDir() {
        String strPath = env.getRequiredProperty("dataDir");
        Path path = Paths.get(strPath);
        Assert.state(Files.exists(path), "dataDir '" + strPath + "' does not exist.");
        return path;
    }

    @Bean
    public ChamberRepository chamberRepository(Path dataDir) {
        return new ChamberRepository(dataDir);
    }

    @Bean
    public ChamberManager chamberManager(ChamberRepository chamberRepository) {
        boolean useDummyChamberManager = PropertyUtils.getBoolean("dummy.chambers", false);
        logger.info("Using " + (useDummyChamberManager ? "DummyChamberManager" : "ArduinoChamberManager"));
        return useDummyChamberManager ? new DummyChamberManager(chamberRepository)
                : new ArduinoChamberManager(chamberRepository);
    }

    public static void main(String[] args) {
        SpringApplication.run(TempctrlApplication.class, args);
    }

    /**
     * Sets the app time zone to UTC if necessary, i.e. if the server's time zone is
     * not UTC.
     */
    private static void ensureAppTimeZoneIsUtc() {
        TimeZone serverTimeZone = TimeZone.getDefault();
        TimeZone timeZone = TimeZone.getTimeZone("Etc/UTC");

        if (serverTimeZone.equals(timeZone)) {
            logger.info("Server/application time zone is " + prettyPrintTimeZone(timeZone) + ".");
        } else {
            TimeZone.setDefault(timeZone);
            logger.info("Server time zone is " + prettyPrintTimeZone(serverTimeZone) + ".");
            logger.info("Application time zone set to " + prettyPrintTimeZone(timeZone) + ".");
        }

        if (timeZone.useDaylightTime())
            fail("The application cannot use a time zone that uses daylight saving time.");
    }

    private static String prettyPrintTimeZone(TimeZone tz) {
        return tz.getID() + (tz.useDaylightTime() ? " (uses DST)" : "");
    }

    /** Logs reason for a failure during static initialisation and exits. */
    private static void fail(String reason) {
        logger.error(reason);
        System.out.println("\n\n******** " + reason + " ********\n\n");
        throw new IllegalStateException(reason);
        // Note: There is no need to do a System.exit(). Throwing the above exception
        // from this static initialisation leaves the app not fully initialised. The
        // container responds with a 404 error to all requests.
    }

}
