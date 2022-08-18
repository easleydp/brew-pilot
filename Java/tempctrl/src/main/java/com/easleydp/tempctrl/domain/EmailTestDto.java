package com.easleydp.tempctrl.domain;

public class EmailTestDto {
    private String subject;
    private String text;
    private boolean noRetry;

    public EmailTestDto() {
    }

    public EmailTestDto(String subject, String text, boolean noRetry) {
        this.subject = subject;
        this.text = text;
        this.noRetry = noRetry;
    }

    public String getSubject() {
        return this.subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean getNoRetry() {
        return this.noRetry;
    }

    public void setNoRetry(boolean noRetry) {
        this.noRetry = noRetry;
    }

    @Override
    public String toString() {
        return "{" + " subject='" + subject + "'" + " text='" + text + "'" + " noRetry=" + noRetry + "}";
    }
}
