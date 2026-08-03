package com.easleydp.tempctrl.spring.config;

import java.util.ArrayList;
import java.util.List;

public class PublicIpChange {
    private List<String> ipProviders = new ArrayList<>();
    private int periodHours;
    private String dnsUpdateUrl;

    public int getPeriodHours() {
        return periodHours;
    }

    public void setPeriodHours(int periodHours) {
        this.periodHours = periodHours;
    }

    public List<String> getIpProviders() {
        return ipProviders;
    }

    public void setIpProviders(List<String> ipProviders) {
        this.ipProviders = ipProviders;
    }

    public String getDnsUpdateUrl() {
        return dnsUpdateUrl;
    }

    public void setDnsUpdateUrl(String dnsUpdateUrl) {
        this.dnsUpdateUrl = dnsUpdateUrl;
    }
}
