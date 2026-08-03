package com.easleydp.tempctrl.spring;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.easleydp.tempctrl.spring.config.PublicIpChange;

public class IpAddressUtils {
    private static final Logger logger = LoggerFactory.getLogger(IpAddressUtils.class);

    private final RestTemplate restTemplate = createRestTemplateWithTimeouts();
    private final List<String> ipProviders;
    private final String dnsUpdateUrl;

    public IpAddressUtils(PublicIpChange publicIpChange) {
        ipProviders = publicIpChange.getIpProviders();
        dnsUpdateUrl = publicIpChange.getDnsUpdateUrl();
    }

    private RestTemplate createRestTemplateWithTimeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // Set timeouts, so RestTemplate will throw if connection
        // can't be made in 3s or if response takes longer than 3s once connected
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);

        return new RestTemplate(factory);
    }

    public String getLocalIP() {
        // Credit: https://stackoverflow.com/a/38342964/65555
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public String getPublicIP() {
        for (String ipProvider : ipProviders) {
            String fetchedIp = getPublicIP(ipProvider);
            if (fetchedIp != null)
                return fetchedIp;
        }
        logger.warn("All providers failed");
        return null;
    }

    /**
     * @return Public IP address or null in the event of any kind of failure (in
     *         which case an error will have already been logged)
     */
    String getPublicIP(String ipProvider) {
        try {
            // Fetch public IP as plain text
            String fetchedIp = restTemplate.getForObject(ipProvider, String.class);

            if (fetchedIp == null || fetchedIp.isBlank()) {
                throw new IllegalStateException("Empty response");
            }

            return fetchedIp.trim();

        } catch (Exception e) {
            // Log network errors gracefully (e.g. temporary internet dropouts or timeouts).
            // Log as error since we want to hear about it (via email).
            logger.error("Failed to check public IP via {}: {}", ipProvider, e.getMessage());
            return null;
        }
    }

    public boolean canUpdateDns() {
        return !(dnsUpdateUrl == null || dnsUpdateUrl.isBlank());
    }

    /**
     * @return null if successful, or a failure reason (in
     *         which case an error will have already been logged).
     */
    public String updateDns() {
        if (!canUpdateDns())
            throw new IllegalStateException("Should not be called unless canUpdateDns() returns true");
        try {
            // Sends the GET request; expects no body back
            restTemplate.getForEntity(dnsUpdateUrl, Void.class);
            logger.debug("Successfully updated DNS record.");
            return null;
        } catch (Exception e) {
            // Catches connection timeouts, 4xx/5xx HTTP codes, and network drops
            String msg = e.getMessage();
            logger.error("Failed to update DNS record: {}", msg);
            return msg;
        }
    }

}
