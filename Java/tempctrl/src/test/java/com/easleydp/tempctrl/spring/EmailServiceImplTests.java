package com.easleydp.tempctrl.spring;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = { "spring.profiles.active: test", "app.mail.from: test@example.com",
        "app.mail.to: test@example.com" })
@DirtiesContext
class EmailServiceImplTests {
    @Autowired
    private EmailService emailService;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void sendSimpleMessageRunsAsynchronously() throws Exception {
        CountDownLatch messageSent = new CountDownLatch(1);
        AtomicReference<Thread> sendingThread = new AtomicReference<>();
        Thread callingThread = Thread.currentThread();
        doAnswer(invocation -> {
            sendingThread.set(Thread.currentThread());
            messageSent.countDown();
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        emailService.sendSimpleMessage("Subject", "Text");

        assertTrue(messageSent.await(5, TimeUnit.SECONDS), "Expected message to be sent");
        assertNotSame(callingThread, sendingThread.get(), "Email sending should run on an async thread");
    }

    @Test
    void sendSimpleMessageNoRetryRemainsSynchronous() {
        AtomicReference<Thread> sendingThread = new AtomicReference<>();
        Thread callingThread = Thread.currentThread();
        doAnswer(invocation -> {
            sendingThread.set(Thread.currentThread());
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        emailService.sendSimpleMessage_noRetry("Subject", "Text");

        assertSame(callingThread, sendingThread.get());
    }
}
