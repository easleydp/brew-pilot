package com.easleydp.tempctrl.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private Environment env;

    @Override
    @Async
    public void sendSimpleMessage(String subject, String text) {
        String from = env.getProperty("mail.from");
        String to = env.getProperty("mail.to");
        boolean smtpIsConfigured = to != null  &&  from != null;

        logger.debug(
            "{}\n\tSubject: {}\n\tText: {}",
            smtpIsConfigured ? "Sending mail message:" : "Mail not configured. Would have sent:",
            subject, text);

        if (smtpIsConfigured) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            logger.debug("\t... async sending of mail complete.");
        }
    }

}
