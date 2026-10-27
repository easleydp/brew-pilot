package com.easleydp.tempctrl.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class EmailSender {
    private static final Logger logger = LoggerFactory.getLogger(EmailSender.class);

    @Autowired(required = false) // 'required = false' for sake of tests (it is required in prod)
    private JavaMailSender mailSender;

    @Autowired
    private Environment env;

    // This public method is invoked through this separate Spring bean's proxy from
    // EmailServiceImpl, so retry advice can catch failures from the actual mail send.
    // Keeping @Retryable here, rather than combining it with @Async, ensures retries
    // run in the asynchronous task instead of ending when that task is merely submitted.
    @Retryable(value = MailSendException.class, maxAttempts = 4, backoff = @Backoff(delay = 60L
            * 1000, multiplier = 2.0))
    public void sendSimpleMessage(String subject, String text) {
        sendMessage(subject, text);
    }

    public void sendSimpleMessage_noRetry(String subject, String text) {
        sendMessage(subject, text);
    }

    private void sendMessage(String subject, String text) {
        String from = env.getProperty("mail.from");
        String to = env.getProperty("mail.to");
        boolean smtpIsConfigured = to != null && from != null;

        logger.debug("{}\n\tSubject: {}\n\tText: {}",
                smtpIsConfigured ? "Sending mail message:" : "Mail not configured. Would have sent:", subject, text);

        if (smtpIsConfigured) {
            if (mailSender == null)
                throw new RuntimeException("mailSender dependency has not been injected.");
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            logger.debug("\t... sending of mail complete.");
        }
    }
}
