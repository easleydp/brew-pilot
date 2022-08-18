package com.easleydp.tempctrl.spring;

import static com.easleydp.tempctrl.domain.PropertyUtils.getInteger;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.easleydp.tempctrl.domain.Chamber;
import com.easleydp.tempctrl.domain.ChamberRepository;
import com.easleydp.tempctrl.domain.Gyle;
import com.easleydp.tempctrl.domain.PointDto;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

@Component
public class EmailMessageScheduler {
    private static final Logger logger = LoggerFactory.getLogger(EmailMessageScheduler.class);

    @Autowired
    private EmailService emailService;

    @Autowired
    private StatusController statusController;

    @Autowired
    private ChamberRepository chamberRepository;

    @Scheduled(cron = "${stillAliveMessage.cronSchedule}")
    public void sendStillAliveMessage() throws IOException {
        logger.debug("sendStillAliveMessage called");

        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        String json = writer.writeValueAsString(statusController.buildStatusReportResponse());

        emailService.sendSimpleMessage("BrewPilot server status report", json);
    }

    @Scheduled(fixedRateString = "${coldCrashCheck.periodMinutes}", timeUnit = TimeUnit.MINUTES)
    public void sendColdCrashComingSoonMessage() throws IOException {
        logger.debug("sendColdCrashComingSoonMessage called");
        testableSendColdCrashComingSoonMessage(new Date());
    }

    void testableSendColdCrashComingSoonMessage(Date timeNow) throws IOException {
        for (Chamber chamber : chamberRepository.getChambers()) {
            Gyle latestGyle = chamber.getLatestGyle();
            if (latestGyle != null) {
                Long dtStarted = latestGyle.getDtStarted();
                if (dtStarted != null && dtStarted > 0 && latestGyle.getDtEnded() == null) {
                    PointDto crashStartPoint = latestGyle.getTemperatureProfile().getCrashStartPoint();
                    if (crashStartPoint != null) {
                        // Assuming the checking period is of the order of 30 minutes, we want to
                        // trigger the email when the crash start time - priorNoticeHours is less than
                        // 30 minutes away. So, when this scheduled task next fires in 30 minutes time,
                        // this figure will have gone -ve.

                        long periodMillis = getInteger("coldCrashCheck.periodMinutes", 30) * 1000L * 60L;
                        long priorNoticeMillis = getInteger("coldCrashCheck.priorNoticeHours", 24) * 1000L * 60L * 60L;

                        long timeNowMs = timeNow.getTime();
                        long millisSinceStart = timeNowMs - dtStarted;
                        long millisUntilTrigger = crashStartPoint.getMillisSinceStart() - priorNoticeMillis
                                - millisSinceStart;
                        if (millisUntilTrigger > 0 && millisUntilTrigger < periodMillis) {
                            Date when = new Date(timeNowMs + millisUntilTrigger + priorNoticeMillis);
                            emailService.sendSimpleMessage("Plan to dry hop this gyle on " + dayOfWeek(when) + "?",
                                    chamber.getName() + "'s gyle \"" + latestGyle.getName()
                                            + "\" is due to cold crash on " + on(when) + " at " + at(when) + ".");
                        }
                    }
                }
            }
        }
    }

    /** @return e.g. "Wednesday" */
    private static String dayOfWeek(Date when) {
        return new SimpleDateFormat("EEEE").format(when);
    }

    /** @return e.g. "Wednesday August 17" */
    private static String on(Date when) {
        return new SimpleDateFormat("EEEE MMMM d").format(when);
    }

    /** @return e.g. "3:41pm" */
    private static String at(Date when) {
        return new SimpleDateFormat("K:mma").format(when);
    }
}
