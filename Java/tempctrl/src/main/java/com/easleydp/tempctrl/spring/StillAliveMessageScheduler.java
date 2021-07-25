package com.easleydp.tempctrl.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

@Component
public class StillAliveMessageScheduler {
    private static final Logger logger = LoggerFactory.getLogger(StillAliveMessageScheduler.class);

    @Autowired
    private EmailService emailService;

    @Autowired
    private StatusController statusController;

    @Scheduled(cron = "${stillAliveMessage.periodCron}")
    public void sendStillAliveMessage() throws IOException {
        logger.debug("sendStillAliveMessage called");

        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        String json = writer.writeValueAsString(statusController.buildStatusReportResponse());

        emailService.sendSimpleMessage("BrewPilot server status report", json);
    }
}
