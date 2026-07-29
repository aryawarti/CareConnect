package com.careconnect.configserver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false")
class ConfigServerApplicationTests {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    @Test
    void servesSharedDefaultsFromConfigRepo() {
        // Any client asking for its config must receive the shared application.yml
        // property source — this is the whole point of the service.
        ResponseEntity<String> response =
                rest.getForEntity("http://localhost:" + port + "/api-gateway/default", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("config-repo/application.yml");
        assertThat(response.getBody()).contains("eureka.client.service-url.defaultZone");
    }

    @Test
    void servesServiceSpecificConfig() {
        ResponseEntity<String> response =
                rest.getForEntity("http://localhost:" + port + "/discovery-server/default", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("discovery-server.yml");
    }
}
