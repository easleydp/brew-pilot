package com.easleydp.tempctrl.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import com.easleydp.tempctrl.domain.ChamberRepository;
import com.easleydp.tempctrl.domain.Gyle;

@SpringBootTest
@TestPropertySource(properties = { "spring.profiles.active: test", "coldCrashCheck.periodMinutes: 20",
        "coldCrashCheck.priorNoticeHours: 6", "coldCrashCheck.postCrashDwellHours: 24" })
@DirtiesContext // Hack to allow multiple Spring integration test suites, otherwise the first
                // suite seems to leave the application context instantiated.
class EmailMessageSchedulerTests {

    interface EmailServiceTest extends EmailService {
        public String getSubject();

        public String getText();

        public void reset();
    }

    @TestConfiguration
    static class EmailServiceImplTestContextConfiguration {
        @Bean
        public EmailServiceTest emailService() {
            return new EmailServiceTest() {

                private String subject;
                private String text;

                public String getSubject() {
                    return subject;
                }

                public String getText() {
                    return text;
                }

                @Override
                public void sendSimpleMessage(String subject, String text) {
                    this.subject = subject;
                    this.text = text;
                }

                @Override
                public void sendSimpleMessage_noRetry(String subject, String text) {
                    this.subject = subject;
                    this.text = text;
                }

                @Override
                public void reset() {
                    subject = null;
                    text = null;
                }
            };
        }

        private String subject;

        public String getSubject() {
            return subject;
        }
    }

    @Autowired
    private ChamberRepository chamberRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private EmailMessageScheduler emailMessageScheduler;

    @BeforeEach
    void beforeEach() {
        Gyle latestFermenterGyle = chamberRepository.getChamberById(2).getLatestGyle();
        latestFermenterGyle.setDtStarted(1659830400000L);
    }

    @AfterEach
    void afterEach() {
        Gyle latestFermenterGyle = chamberRepository.getChamberById(2).getLatestGyle();
        latestFermenterGyle.setDtStarted(null);
        ((EmailServiceTest) emailService).reset();
    }

    /*
     * First, a couple of tests to sanity check the test EmailService is working as
     * expected.
     */

    @Test
    void basicSend() {
        emailService.sendSimpleMessage("Test subject", "Test text");

        assertMessageSubjectEquals("Test subject");
        assertMessageTextEquals("Test text");
    }

    @Test
    void noMessage() {
        assertMessageSubjectEquals(null);
        assertMessageTextEquals(null);
    }

    // 1659830400000 + (240*60*60*1000)=1660694400000
    // 1659830400000 + (120*60*60*1000)=1660262400000

    /**
     * EmailMessageScheduler - midFermentation tests.
     *
     * beforeEach sets "dtStarted" to 1659830400000 (2022/08/07 00:00 UTC).
     * "hoursSinceStart" of the profile point at the start of the cold crash is 240,
     * so midpoint occurs after 120 hours,
     * so this maps to a dt of 1660262400000 (Fri 2022/08/12 00:00 UTC).
     *
     * Given that in this test suite we have set the properties as follows:
     * <ul>
     * <li>"coldCrashCheck.periodMinutes: 20"
     * </ul>
     * emails should be triggered at:
     * <ul>
     * <li>[Test case 1] 19 minutes prior to dt
     * <li>[Test case 2] 1 minute prior to dt
     * </ul>
     * and emails should not be triggered at:
     * <ul>
     * <li>[Test case 3] 21 minutes prior to dt
     * <li>[Test case 4] 1 minute after to dt
     * </ul>
     */

    private static final Date midFermentationTime = buildUtcDate(2022, 8, 12, 0, 0);

    @Test
    void testSendMidFermentationMessage_case1() {
        testSendMidFermentationMessage(
                new Date(midFermentationTime.getTime() - hoursAndMinutesInMillis(0, 19)));
    }

    @Test
    void testSendMidFermentationMessage_case2() {
        testSendMidFermentationMessage(
                new Date(midFermentationTime.getTime() - hoursAndMinutesInMillis(0, 1)));
    }

    @Test
    void testSendMidFermentationMessage_case3() {
        testNoMessageSent(new Date(midFermentationTime.getTime() - hoursAndMinutesInMillis(0, 21)));
    }

    @Test
    void testSendMidFermentationMessage_case4() {
        testNoMessageSent(new Date(midFermentationTime.getTime() - hoursAndMinutesInMillis(0, -1)));
    }

    private void testSendMidFermentationMessage(Date timeNow) {
        emailMessageScheduler.testableSendGyleRelatedNotifications(timeNow);

        assertMessageSubjectEquals("Mid-fermentation. Add Aromazyme? 💉");
        assertMessageTextEquals(
                "If needed, Fermentation chamber's gyle \"#45 Reid 1839 BPA\" could be dosed with Aromazyme now.");
    }

    /**
     * EmailMessageScheduler - crashStartPoint tests.
     *
     * beforeEach sets "dtStarted" to 1659830400000 (2022/08/07 00:00 UTC).
     * "hoursSinceStart" of the profile point at the start of the cold crash is 240,
     * so this maps to a dt of 1660694400000 (Wed 2022/08/17 00:00 UTC).
     *
     * Given that in this test suite we have set the properties as follows:
     * <ul>
     * <li>"coldCrashCheck.periodMinutes: 20"
     * <li>"coldCrashCheck.priorNoticeHours: 6"
     * </ul>
     * emails should be triggered at:
     * <ul>
     * <li>[Test case 1] 6 hours and 19 minutes prior to dt
     * <li>[Test case 2] 6 hours and 1 minute prior to dt
     * </ul>
     * and emails should not be triggered at:
     * <ul>
     * <li>[Test case 3] 6 hours and 21 minutes prior to dt
     * <li>[Test case 4] 5 hours and 59 minutes prior to dt
     * </ul>
     */

    private static final Date coldCrashStartTime = buildUtcDate(2022, 8, 17, 0, 0);

    @Test
    void testSendColdCrashComingSoonMessage_case1() {
        testSendColdCrashComingSoonMessage(new Date(coldCrashStartTime.getTime() - hoursAndMinutesInMillis(6, 19)));
    }

    @Test
    void testSendColdCrashComingSoonMessage_case2() {
        testSendColdCrashComingSoonMessage(new Date(coldCrashStartTime.getTime() - hoursAndMinutesInMillis(6, 1)));
    }

    @Test
    void testSendColdCrashComingSoonMessage_case3() {
        testNoMessageSent(new Date(coldCrashStartTime.getTime() - hoursAndMinutesInMillis(6, 21)));
    }

    @Test
    void testSendColdCrashComingSoonMessage_case4() {
        testNoMessageSent(new Date(coldCrashStartTime.getTime() - hoursAndMinutesInMillis(5, 59)));
    }

    private void testSendColdCrashComingSoonMessage(Date timeNow) {
        emailMessageScheduler.testableSendGyleRelatedNotifications(timeNow);

        assertMessageSubjectEquals("Plan to dry hop this gyle soon?");
        // Note: Time should be presented in local time, which for us is BST.
        // Hence, 1:00am instead of 0:00am.
        assertMessageTextEquals(
                "Fermentation chamber's gyle \"#45 Reid 1839 BPA\" is due to cold crash on Wednesday August 17 at 1:00am.");
    }

    /**
     * EmailMessageScheduler - crashEndPoint tests.
     *
     * beforeEach sets "dtStarted" to 1659830400000 (2022/08/07 00:00 UTC).
     * "hoursSinceStart" of the profile point at the end of the cold crash is 246,
     * so this maps to a dt of 1660716000000 (Wed 2022/08/17 06:00 UTC).
     *
     * Given that in this test suite we have set the properties as follows:
     * <ul>
     * <li>"coldCrashCheck.periodMinutes: 20"
     * <li>"coldCrashCheck.postCrashDwellHours: 24"
     * </ul>
     * emails should be triggered at:
     * <ul>
     * <li>[Test case 1] 23 hours and 41 minutes after dt
     * <li>[Test case 2] 23 hours and 59 minutes after dt
     * </ul>
     * and emails should not be triggered at:
     * <ul>
     * <li>[Test case 3] 23 hours and 39 minutes after dt
     * <li>[Test case 4] 24 hours and 1 minute after dt
     * </ul>
     */

    private static final Date endCrashTime = buildUtcDate(2022, 8, 17, 6, 0);

    @Test
    void testSendTimeToBottleMessage_case1() {
        testSendTimeToBottleMessage(new Date(endCrashTime.getTime() + hoursAndMinutesInMillis(23, 41)));
    }

    @Test
    void testSendTimeToBottleMessage_case2() {
        testSendTimeToBottleMessage(new Date(endCrashTime.getTime() + hoursAndMinutesInMillis(23, 59)));
    }

    @Test
    void testSendTimeToBottleMessage_case3() {
        testNoMessageSent(new Date(endCrashTime.getTime() + hoursAndMinutesInMillis(23, 39)));
    }

    @Test
    void testSendTimeToBottleMessage_case4() {
        testNoMessageSent(new Date(endCrashTime.getTime() + hoursAndMinutesInMillis(24, 1)));
    }

    private void testSendTimeToBottleMessage(Date timeNow) {
        emailMessageScheduler.testableSendGyleRelatedNotifications(timeNow);

        assertMessageSubjectEquals("Bottle gyle #45? 🍺");
        assertMessageTextEquals("Fermentation chamber's gyle \"#45 Reid 1839 BPA\" could be bottled any time now.");
    }

    private void testNoMessageSent(Date timeNow) {
        emailMessageScheduler.testableSendGyleRelatedNotifications(timeNow);

        assertMessageSubjectEquals(null);
        assertMessageTextEquals(null);
    }

    /* Helpers */

    private static long hoursAndMinutesInMillis(int hours, int mins) {
        return hours * 1000L * 60 * 60 + mins * 1000L * 60;
    }

    private static Date buildUtcDate(int year, int month /* zero based! */, int date, int hourOfDay, int minute) {
        Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone("UTC"));
        c.set(year, month - 1, date, hourOfDay, minute, 0);
        return c.getTime();
    }

    private void assertMessageSubjectEquals(String expected) {
        EmailServiceTest emailServiceImpl = (EmailServiceTest) emailService;
        assertEquals(expected, emailServiceImpl.getSubject());
    }

    private void assertMessageTextEquals(String expected) {
        EmailServiceTest emailServiceImpl = (EmailServiceTest) emailService;
        assertEquals(expected, emailServiceImpl.getText());
    }

}
