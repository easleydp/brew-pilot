package com.easleydp.tempctrl.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

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
        "coldCrashCheck.priorNoticeHours: 6" })
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
        latestFermenterGyle.setDtStarted(1659826800000L);
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

        assertSubjectEquals("Test subject");
        assertTextEquals("Test text");
    }

    @Test
    void noMessage() {
        assertSubjectEquals(null);
        assertTextEquals(null);
    }

    /**
     * EmailMessageScheduler tests.
     *
     * beforeEach sets "dtStarted" to 1659826800000 (2022/08/07 00:00 local time).
     * "hoursSinceStart" of the profile point ar the start of the cold crash is 240,
     * so this maps to a dt of 1660690800000 (2022/08/17 00:00 local time).
     *
     * Above we have set the properties:
     * <ul>
     * <li>"coldCrashCheck.periodMinutes: 20"</li>
     * <li>"coldCrashCheck.priorNoticeHours: 6"</li>
     * </ul>
     *
     * So, emails should be triggered at:
     * <ul>
     * <li>Test case 1: 6 hours and 19 minutes prior to dt</li>
     * <li>Test case 2: 6 hours and 1 minute prior to dt</li>
     * </ul>
     *
     * And emails should not be triggered at:
     * <ul>
     * <li>Test case 3: 6 hours and 21 minutes prior to dt</li>
     * <li>Test case 4: 5 hours 59 minutes prior to dt</li>
     * </ul>
     */

    private static final Date triggerTime = buildDate(2022, 8, 17, 0, 0);

    @Test
    void testSendColdCrashComingSoonMessage_case1() throws IOException {
        Date timeNow = new Date(triggerTime.getTime() - hoursAndMinutesInMillis(6, 19));
        emailMessageScheduler.testableSendColdCrashComingSoonMessage(timeNow);

        assertSubjectEquals("Plan to dry hop this gyle on Wednesday?");
        assertTextEquals(
                "Fermentation chamber's gyle \"#45 Reid 1839 BPA\" is due to cold crash on Wednesday August 17 at 0:00am.");
    }

    @Test
    void testSendColdCrashComingSoonMessage_case2() throws IOException {
        Date timeNow = new Date(triggerTime.getTime() - hoursAndMinutesInMillis(6, 1));
        emailMessageScheduler.testableSendColdCrashComingSoonMessage(timeNow);

        assertSubjectEquals("Plan to dry hop this gyle on Wednesday?");
        assertTextEquals(
                "Fermentation chamber's gyle \"#45 Reid 1839 BPA\" is due to cold crash on Wednesday August 17 at 0:00am.");
    }

    @Test
    void testSendColdCrashComingSoonMessage_case3() throws IOException {
        Date timeNow = new Date(triggerTime.getTime() - hoursAndMinutesInMillis(6, 21));
        emailMessageScheduler.testableSendColdCrashComingSoonMessage(timeNow);

        assertSubjectEquals(null);
        assertTextEquals(null);
    }

    @Test
    void testSendColdCrashComingSoonMessage_case4() throws IOException {
        Date timeNow = new Date(triggerTime.getTime() - hoursAndMinutesInMillis(5, 59));
        emailMessageScheduler.testableSendColdCrashComingSoonMessage(timeNow);

        assertSubjectEquals(null);
        assertTextEquals(null);
    }

    /* Helpers */

    private static long hoursAndMinutesInMillis(int hours, int mins) {
        return hours * 1000L * 60 * 60 + mins * 1000L * 60;
    }

    private static Date buildDate(int year, int month /* zero based! */, int date, int hourOfDay, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(year, month - 1, date, hourOfDay, minute);
        return c.getTime();
    }

    private void assertSubjectEquals(String expected) {
        EmailServiceTest emailServiceImpl = (EmailServiceTest) emailService;
        assertEquals(expected, emailServiceImpl.getSubject());
    }

    private void assertTextEquals(String expected) {
        EmailServiceTest emailServiceImpl = (EmailServiceTest) emailService;
        // String actual = emailServiceImpl.getText();
        // if (!expected.equals(actual)) {
        // System.out.println(">>>>> [" + StringUtils.difference(expected, actual) +
        // "]");
        // int i = StringUtils.indexOfDifference(expected, actual);
        // System.out.println(">>>>> [" + expected.substring(0, i) + "]");
        // System.out.println(">>>>> [" + actual.substring(0, i) + "]");
        // }
        assertEquals(expected, emailServiceImpl.getText());
    }

}
