package com.rover.ac13.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ErrorPageIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void missingHtmlRouteReturnsCustom404Page() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.TEXT_HTML));

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/definitely-missing",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("404 - AC13 Web Controller"));
    }

    @Test
    void missingJsonRouteReturnsStructuredErrorJson() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/definitely-missing",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"status\":404"));
        assertTrue(response.getBody().contains("\"path\":\"/definitely-missing\""));
    }

    @Test
    void actuatorHealthEndpointReturnsUp() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/actuator/health",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"status\":\"UP\""));
    }

    @Test
    void actuatorInfoEndpointExposesBuildMetadata() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/actuator/info",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"build\""));
        assertTrue(response.getBody().contains("\"version\":\"1.0.0-SNAPSHOT\""));
        assertTrue(response.getBody().contains("local"));
    }

    @Test
    void actuatorReadinessAndLivenessEndpointsReturnUp() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> readiness = restTemplate.exchange(
                "http://localhost:" + port + "/actuator/health/readiness",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );
        ResponseEntity<String> liveness = restTemplate.exchange(
                "http://localhost:" + port + "/actuator/health/liveness",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertEquals(HttpStatus.OK, readiness.getStatusCode());
        assertEquals(HttpStatus.OK, liveness.getStatusCode());
        assertNotNull(readiness.getBody());
        assertNotNull(liveness.getBody());
        assertTrue(readiness.getBody().contains("\"status\":\"UP\""));
        assertTrue(liveness.getBody().contains("\"status\":\"UP\""));
    }
}
