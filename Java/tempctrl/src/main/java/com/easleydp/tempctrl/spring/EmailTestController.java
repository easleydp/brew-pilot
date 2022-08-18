package com.easleydp.tempctrl.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.easleydp.tempctrl.domain.EmailTestDto;

@RestController
public class EmailTestController {
    private static final Logger logger = LoggerFactory.getLogger(EmailTestController.class);

    @Autowired
    private EmailService emailService;

    @PostMapping("/admin/email-test")
    public void sendEmail(@RequestBody EmailTestDto emailTest) {
        logger.info("POST email-test\n\t" + emailTest);
        if (emailTest.getNoRetry())
            emailService.sendSimpleMessage_noRetry(emailTest.getSubject(), emailTest.getText());
        else
            emailService.sendSimpleMessage(emailTest.getSubject(), emailTest.getText());
    }
}
