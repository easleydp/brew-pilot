package com.easleydp.tempctrl.spring;

import static org.apache.commons.lang3.time.DateUtils.addHours;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.easleydp.tempctrl.domain.PropertyUtils;
import com.easleydp.tempctrl.util.OsCommandExecuter;

@Component
public class OfflineCheckScheduler {
    private static final Logger logger = LoggerFactory.getLogger(OfflineCheckScheduler.class);

    private static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String pingCount = isWindows ? "-n" : "-c";

    // Chronological list of timestamps when server was found to be offline, latest
    // last. So, new items are added to the end of list while expired items are
    // purged from the front.
    private List<Date> buffer = new ArrayList<>();

    @Scheduled(cron = "${offlineCheck.cronSchedule}")
    public void checkOffline() {
        logger.debug("checkOffline called");
        if (!onlineDoubleCheck()) {
            Date timeNow = new Date();
            purgeBuffer(timeNow);
            buffer.add(timeNow);
        }
    }

    private boolean online() {
        String address = PropertyUtils.getString("offlineCheck.routerAddress");
        String stdout = OsCommandExecuter.executeWithNoExitCodeError("ping", pingCount, "1", address);
        return stdout != null; // A diagnostic warning message will be logged if null
    }

    /**
     * It seems that our first call to `ping` after a period of inactivity is
     * somewhat unreliable, so regard first attempt as a warm up.
     */
    private boolean onlineDoubleCheck() {
        if (online()) {
            return true;
        }
        logger.warn("First ping attempt failed. Trying again...");
        return online();
    }

    private void purgeBuffer(Date timeNow) {
        Date expired = addHours(timeNow, -1 * PropertyUtils.getInt("offlineCheck.bufferSizeHrs"));
        buffer.removeIf(d -> d.before(expired));
    }

    public List<Date> getRecentlyOffline(Date timeNow) {
        purgeBuffer(timeNow);
        return Collections.unmodifiableList(buffer);
    }

}
