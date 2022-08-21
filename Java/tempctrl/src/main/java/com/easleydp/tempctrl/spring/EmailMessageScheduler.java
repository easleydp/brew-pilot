package com.easleydp.tempctrl.spring;

import static com.easleydp.tempctrl.domain.PropertyUtils.getInteger;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
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
import com.easleydp.tempctrl.domain.PropertyUtils;
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
    public void sendGyleRelatedNotifications() {
        logger.debug("sendGyleRelatedNotifications called");
        testableSendGyleRelatedNotifications(new Date());
    }

    void testableSendGyleRelatedNotifications(Date timeNow) {
        for (Chamber chamber : chamberRepository.getChambers()) {
            Gyle latestGyle = chamber.getLatestGyle();
            if (latestGyle != null) {
                Long dtStarted = latestGyle.getDtStarted();
                if (dtStarted != null && dtStarted > 0 && latestGyle.getDtEnded() == null) {
                    long periodMillis = getInteger("coldCrashCheck.periodMinutes", 30) * 1000L * 60L;
                    long timeNowMs = timeNow.getTime();
                    long millisSinceStart = timeNowMs - dtStarted;
                    maybeSendCrashStartNotification(periodMillis, timeNowMs, millisSinceStart, latestGyle, chamber);
                    maybeSendCrashEndNotification(periodMillis, timeNowMs, millisSinceStart, latestGyle, chamber);
                }
            }
        }
    }

    private void maybeSendCrashStartNotification(long periodMillis, long timeNowMs, long millisSinceStart,
            Gyle latestGyle, Chamber chamber) {
        PointDto crashStartPoint = latestGyle.getTemperatureProfile().getCrashStartPoint();
        if (crashStartPoint != null) {
            // Assuming the checking period is of the order of 30 minutes, we want to
            // trigger the email when the crash start time - priorNoticeHours is less than
            // 30 minutes away. So, when this scheduled task next fires in 30 minutes time,
            // this figure will have gone -ve.
            long priorNoticeMillis = getInteger("coldCrashCheck.priorNoticeHours", 36) * 1000L * 60 * 60;

            long millisUntilTrigger = crashStartPoint.getMillisSinceStart() - priorNoticeMillis - millisSinceStart;
            if (millisUntilTrigger > 0 && millisUntilTrigger < periodMillis) {
                Date when = new Date(timeNowMs + millisUntilTrigger + priorNoticeMillis);
                emailService.sendSimpleMessage("Plan to dry hop this gyle on " + dayOfWeek(when) + "?",
                        chamber.getName() + "'s gyle \"" + latestGyle.getName() + "\" is due to cold crash on "
                                + on(when) + " at " + at(when) + ".");
            }
        }
    }

    private void maybeSendCrashEndNotification(long periodMillis, long timeNowMs, long millisSinceStart,
            Gyle latestGyle, Chamber chamber) {
        PointDto crashEndPoint = latestGyle.getTemperatureProfile().getCrashEndPoint();
        if (crashEndPoint != null) {
            // Assuming the checking period is of the order of 30 minutes, we want to
            // trigger the email when the crash end time + postCrashDwellHours is less than
            // 30 minutes away. So, when this scheduled task next fires in 30 minutes time,
            // this figure will have gone -ve.
            long postCrashDwellMillis = getInteger("coldCrashCheck.postCrashDwellHours", 48) * 1000L * 60 * 60;

            long millisUntilTrigger = crashEndPoint.getMillisSinceStart() + postCrashDwellMillis - millisSinceStart;
            if (millisUntilTrigger > 0 && millisUntilTrigger < periodMillis) {
                Date when = new Date(timeNowMs + millisUntilTrigger);
                emailService.sendSimpleMessage("Bottle this gyle on " + dayOfWeek(when) + "?",
                        chamber.getName() + "'s gyle \"" + latestGyle.getName() + "\" could be bottled on " + on(when)
                                + " at " + at(when) + ".");
            }
        }
    }

    /** @return e.g. "Wednesday" */
    private static String dayOfWeek(Date when) {
        return formatAsLocalTime("EEEE", when);
    }

    /** @return e.g. "Wednesday August 17" */
    private static String on(Date when) {
        return formatAsLocalTime("EEEE MMMM d", when);
    }

    /** @return e.g. "3:41pm" (local time) */
    private static String at(Date when) {
        return formatAsLocalTime("K:mma", when);
    }

    private static final String TIMEZONE_LOCAL_ID = PropertyUtils.getString("timezone.localId", "Europe/London");

    private static String formatAsLocalTime(String format, Date when) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        sdf.setTimeZone(TimeZone.getTimeZone(TIMEZONE_LOCAL_ID));
        return sdf.format(when);
    }
}
