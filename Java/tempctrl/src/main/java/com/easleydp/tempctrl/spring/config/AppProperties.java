package com.easleydp.tempctrl.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private PublicIpChange publicIpChange;

    public PublicIpChange getPublicIpChange() {
        return publicIpChange;
    }

    public void setPublicIpChange(PublicIpChange publicIpChange) {
        this.publicIpChange = publicIpChange;
    }

}
