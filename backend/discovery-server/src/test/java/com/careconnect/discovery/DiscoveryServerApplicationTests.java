package com.careconnect.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false"
})
class DiscoveryServerApplicationTests {

    @Test
    void eurekaServerContextLoads() {
        // Guards the platform wiring: dependency versions, @EnableEurekaServer,
        // and standalone-mode config must produce a bootable context.
    }
}
