package com.rover.ac13.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "rover.api.token=it-secret")
@AutoConfigureTestRestTemplate
class ApiTokenIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    private ResponseEntity<String> post(String path, HttpHeaders headers) {
        return restTemplate.exchange("http://localhost:" + port + path, HttpMethod.POST,
                new HttpEntity<>(headers), String.class);
    }

    @Test
    void mutatingRequestWithoutTokenIsRejectedWithCorrelationId() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CorrelationIdFilter.HEADER, "it-correlation");

        ResponseEntity<String> response = post("/api/stop", headers);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("it-correlation", response.getHeaders().getFirst(CorrelationIdFilter.HEADER));
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"correlationId\":\"it-correlation\""));
    }

    @Test
    void mutatingRequestWithTokenIsAccepted() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("it-secret");

        assertEquals(HttpStatus.OK, post("/api/stop", headers).getStatusCode());
    }

    @Test
    void readOnlyRequestStaysPublic() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/status", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
