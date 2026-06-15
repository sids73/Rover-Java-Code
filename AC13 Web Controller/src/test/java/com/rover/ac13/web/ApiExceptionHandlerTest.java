package com.rover.ac13.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleTypeMismatchReturnsBadRequestWithParameterName() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("speed");

        HttpServletRequest request = mockRequest("/api/move/forward", "corr-type");

        ResponseEntity<ApiError> response = handler.handleTypeMismatch(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Invalid parameter: speed", response.getBody().message());
        assertEquals("/api/move/forward", response.getBody().path());
        assertEquals("corr-type", response.getBody().correlationId());
    }

    @Test
    void handleUnexpectedReturnsInternalServerError() {
        HttpServletRequest request = mockRequest("/api/status", "corr-unexpected");

        ResponseEntity<ApiError> response = handler.handleUnexpected(new RuntimeException("boom"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().status());
        assertEquals("Internal Server Error", response.getBody().error());
        assertEquals("Unexpected server error", response.getBody().message());
        assertEquals("/api/status", response.getBody().path());
        assertEquals("corr-unexpected", response.getBody().correlationId());
    }

    private static HttpServletRequest mockRequest(String uri, String correlationId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE)).thenReturn(correlationId);
        return request;
    }
}
