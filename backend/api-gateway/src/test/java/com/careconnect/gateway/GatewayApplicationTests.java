package com.careconnect.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        // A real secret, not the repository default: InsecureDefaultsGuard
        // refuses to start on a published one, so this also asserts that the
        // guard stays quiet for a properly configured gateway.
        "careconnect.jwt.secret=context-test-secret-key-long-enough-for-hs256",
        "careconnect.platform.gateway-secret=context-test-gateway-secret"
})
class GatewayApplicationTests {

    @Test
    void gatewayContextLoads() {
    }
}
