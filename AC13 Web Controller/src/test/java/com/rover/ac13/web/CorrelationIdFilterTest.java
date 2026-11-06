package com.rover.ac13.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private static void assertFreshUuid(String id) {
        assertNotNull(id);
        assertDoesNotThrow(() -> UUID.fromString(id));
    }

    @Test
    void keepsSafeIds() {
        assertEquals("abc-123", CorrelationIdFilter.sanitize("abc-123"));
        assertEquals("trace.id_1:2-3", CorrelationIdFilter.sanitize("trace.id_1:2-3"));
        String uuid = UUID.randomUUID().toString();
        assertEquals(uuid, CorrelationIdFilter.sanitize(uuid));
    }

    @Test
    void acceptsUpToSixtyFourCharacters() {
        String max = "a".repeat(64);

        assertEquals(max, CorrelationIdFilter.sanitize(max));
        assertFreshUuid(CorrelationIdFilter.sanitize("a".repeat(65)));
    }

    @Test
    void replacesMissingOrBlankIds() {
        assertFreshUuid(CorrelationIdFilter.sanitize(null));
        assertFreshUuid(CorrelationIdFilter.sanitize(""));
        assertFreshUuid(CorrelationIdFilter.sanitize("   "));
    }

    @Test
    void replacesIdsWithCrLfOrOtherUnsafeCharacters() {
        assertFreshUuid(CorrelationIdFilter.sanitize("abc\r\nSet-Cookie: session=evil"));
        assertFreshUuid(CorrelationIdFilter.sanitize("abc\ndef"));
        assertFreshUuid(CorrelationIdFilter.sanitize("abc\rdef"));
        assertFreshUuid(CorrelationIdFilter.sanitize("has space"));
        assertFreshUuid(CorrelationIdFilter.sanitize("<script>alert(1)</script>"));
        assertFreshUuid(CorrelationIdFilter.sanitize("café"));
    }

    @Test
    void responseSplittingPayloadNeverReachesTheResponse() throws Exception {
        String payload = "abc\r\nContent-Length: 0\r\n\r\nHTTP/1.1 200 OK\r\nSet-Cookie: session=evil";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/status");
        request.addHeader(CorrelationIdFilter.HEADER, payload);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new CorrelationIdFilter().doFilter(request, response, chain);

        String echoed = response.getHeader(CorrelationIdFilter.HEADER);
        assertFreshUuid(echoed);
        assertFalse(echoed.contains("\r") || echoed.contains("\n"));
        assertNull(response.getHeader("Set-Cookie"));
        assertEquals(echoed, request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE));
        assertNotNull(chain.getRequest(), "request should continue down the chain");
    }

    @Test
    void safeIncomingIdIsEchoedAndStored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/status");
        request.addHeader(CorrelationIdFilter.HEADER, "client-trace-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorrelationIdFilter().doFilter(request, response, new MockFilterChain());

        assertEquals("client-trace-42", response.getHeader(CorrelationIdFilter.HEADER));
        assertEquals("client-trace-42", request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE));
    }
}
