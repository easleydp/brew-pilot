package com.easleydp.tempctrl.spring;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {
    private final EmailSender emailSender;

    public EmailServiceImpl(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    // Keep asynchronous dispatch on this public, externally invoked method so Spring's proxy
    // can intercept it. The separate EmailSender bean applies retries inside the async task;
    // combining @Async and @Retryable here would make retry advice observe only task submission,
    // not failures raised later while sending. Moving @Async to a private helper called from
    // this class would also bypass proxy interception through self-invocation.
    @Override
    @Async
    public void sendSimpleMessage(String subject, String text) {
        emailSender.sendSimpleMessage(subject, text);
    }

    @Override
    public void sendSimpleMessage_noRetry(String subject, String text) {
        emailSender.sendSimpleMessage_noRetry(subject, text);
    }
}
