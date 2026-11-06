package com.rover.ac13.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiTokenFilterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    /** Runs the filter and returns the response; chain.getRequest() is non-null if the request was let through. */
    private static MockHttpServletResponse run(ApiTokenFilter filter, MockHttpServletRequest request, MockFilterChain chain)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void blankTokenDisablesCheck() throws Exception {
        ApiTokenFilter filter = new ApiTokenFilter("  ", JSON);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(filter, request("POST", "/api/connect"), chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void nullTokenDisablesCheck() throws Exception {
        ApiTokenFilter filter = new ApiTokenFilter(null, JSON);
        MockFilterChain chain = new MockFilterChain();

        run(filter, request("POST", "/api/stop"), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void readOnlyRequestsArePublic() throws Exception {
        ApiTokenFilter filter = new ApiTokenFilter("secret", JSON);
        MockFilterChain chain = new MockFilterChain();

        run(filter, request("GET", "/api/status"), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void nonApiPathsArePublic() throws Exception {
        ApiTokenFilter filter = new ApiTokenFilter("secret", JSON);
        MockFilterChain chain = new MockFilterChain();

        run(filter, request("POST", "/actuator/refresh"), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void missingTokenIsRejectedWithStructuredError() throws Exception {
        ApiTokenFilter filter = new ApiTokenFilter("secret", JSON);
        MockHttpServletRequest request = request("POST", "/api/move/forward");
        request.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, "cid-1");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(filter, request, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        JsonNode body = JSON.readTree(response.getContentAsString());
        assertEquals(401, body.get("status").asInt());
        assertEquals("Unauthorized", body.get("error").asString());
        assertEquals("Missing or invalid API token", body.get("message").asString());
        assertEquals("/api/move/forward", body.get("path").asString());
        assertEquals("cid-1", body.get("correlationId").asString());
    }

    @Test
    void nonBearerSchemeIsRejected() throws Exception {
        ApiTokenFilter filter = new ApiTokenFilter("secret", JSON);
        MockHttpServletRequest request = request("POST", "/api/stop");
        request.addHeader("Authorization", "Basic secret");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(filter, request, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void wrongTokenIsRejected() throws Exception {
        ApiTokenFilter filter = new ApiTokenFilter("secret", JSON);
        MockHttpServletRequest request = request("POST", "/api/stop");
        request.addHeader("Authorization", "Bearer wrong");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(filter, request, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void correctTokenIsAccepted() throws Exception {
        ApiTokenFilter filter = new ApiTokenFilter("secret", JSON);
        MockHttpServletRequest request = request("POST", "/api/stop");
        request.addHeader("Authorization", "Bearer secret");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(filter, request, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }
}
