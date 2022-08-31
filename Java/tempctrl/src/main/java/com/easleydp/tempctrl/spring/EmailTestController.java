package com.easleydp.tempctrl.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailTestController {
    private static final Logger logger = LoggerFactory.getLogger(EmailTestController.class);

    @Autowired
    private EmailService emailService;

    @PostMapping("/admin/email-test")
    public void sendEmail(@RequestBody EmailTestDto emailTest) {
        logger.info("POST email-test\n\t{}", emailTest);
        if (emailTest.noRetry)
            emailService.sendSimpleMessage_noRetry(emailTest.subject, emailTest.text);
        else
            emailService.sendSimpleMessage(emailTest.subject, emailTest.text);
    }

    private static final class EmailTestDto {
        public String subject;
        public String text;
        public boolean noRetry;

        @Override
        public String toString() {
            return "{" + " subject='" + subject + "'" + " text='" + text + "'" + " noRetry=" + noRetry + "}";
        }
    }
}
