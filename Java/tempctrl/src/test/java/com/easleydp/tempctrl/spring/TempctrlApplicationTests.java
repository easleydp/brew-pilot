package com.easleydp.tempctrl.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.SpringVersion;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import com.easleydp.tempctrl.domain.ChamberRepository;

@SpringBootTest
@TestPropertySource(properties = { "spring.profiles.active: test" })
@DirtiesContext // Hack to allow multiple Spring integration test suites, otherwise the first
                // suite seems to leave the application context instantiated.
class TempctrlApplicationTests {

    @Test
    void contextLoads() {
        System.out.println("Spring version: " + SpringVersion.getVersion());
        System.out.println("Spring Boot version: " + SpringBootVersion.getVersion());
    }

}
