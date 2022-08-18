package com.easleydp.tempctrl.spring;

public interface EmailService {
    // With retries
    public void sendSimpleMessage(String subject, String text);

    // Without retries (typically only used for test purposes)
    public void sendSimpleMessage_noRetry(String subject, String text);
}
