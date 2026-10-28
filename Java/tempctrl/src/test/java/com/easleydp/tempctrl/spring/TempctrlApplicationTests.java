package com.easleydp.tempctrl.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.SpringVersion;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.easleydp.tempctrl.spring.config.AppProperties;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "spring.profiles.active: test" })
@DirtiesContext // Hack to allow multiple Spring integration test suites, otherwise the first
                // suite seems to leave the application context instantiated.
class TempctrlApplicationTests {
    @Autowired
    private AppProperties appProperties;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
        System.out.println("Spring version: " + SpringVersion.getVersion());
        System.out.println("Spring Boot version: " + SpringBootVersion.getVersion());
    }

    @Test
    void customPropertiesBindFromAppNamespace() {
        assertEquals("192.168.1.1", appProperties.getOfflineCheck().getRouterAddress());
        assertEquals(24, appProperties.getOfflineCheck().getBufferSizeHours());
        assertEquals(60000, appProperties.getReadings().getPeriodMillis());
        assertEquals(30, appProperties.getReadings().getGen().getGen1ReadingsCount());
        assertEquals(10, appProperties.getReadings().getGen().getMultiplier());
        assertEquals(4, appProperties.getReadings().getGen().getMax());
        assertEquals(2, appProperties.getReadings().getTemp().getSmoothing().getThresholdHeight());
        assertTrue(appProperties.getReadings().getOptimise().isSmoothTemperatureReadings());
        assertTrue(appProperties.getReadings().getOptimise().isNullOutRedundantValues());
        assertTrue(appProperties.getReadings().getOptimise().isRemoveRedundantIntermediate());
        assertTrue(appProperties.getReadings().isStaggerFirstReadings());
    }

    @Test
    void loginRequiresAndAcceptsCsrfToken() throws Exception {
        mockMvc.perform(post("/login").param("username", "admin").param("password", "admin"))
                .andExpect(status().isForbidden());

        MvcResult csrfResult = mockMvc.perform(get("/csrf")).andExpect(status().isNoContent()).andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        assertNotNull(session);

        mockMvc.perform(post("/login")
                .session(session)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .param("username", "admin")
                .param("password", "admin"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"result\":\"OK\", \"isAdmin\":true}"));
    }

}
