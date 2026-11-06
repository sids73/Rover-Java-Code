package com.rover.ac13.web;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoverController.class)
class RoverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoverService roverService;

    @Test
    void statusEndpointReturnsJson() throws Exception {
        when(roverService.status()).thenReturn(Map.of("connected", false, "streaming", false, "infrared", "Off"));

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.infrared").value("Off"));
    }

    @Test
    void connectReturnsBadGatewayWhenConnectFails() throws Exception {
        when(roverService.connect()).thenReturn(false);
        when(roverService.status()).thenReturn(Map.of("connected", false, "streaming", false, "infrared", "Off"));

        mockMvc.perform(post("/api/connect"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    void connectReturnsOkWhenConnectSucceeds() throws Exception {
        when(roverService.connect()).thenReturn(true);
        when(roverService.status()).thenReturn(Map.of("connected", true, "streaming", true, "infrared", "On"));

        mockMvc.perform(post("/api/connect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true));
    }

    @Test
    void disconnectEndpointReturnsOkAndDelegates() throws Exception {
        when(roverService.status()).thenReturn(Map.of("connected", false, "streaming", false, "infrared", "Off"));

        mockMvc.perform(post("/api/disconnect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false));

        verify(roverService).disconnect();
    }

    @Test
    void toggleInfraredEndpointReturnsOkAndDelegates() throws Exception {
        when(roverService.status()).thenReturn(Map.of("connected", true, "streaming", true, "infrared", "On"));

        mockMvc.perform(post("/api/infrared/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.infrared").value("On"));

        verify(roverService).toggleInfrared();
    }

    @Test
    void stopEndpointReturnsOkAndDelegates() throws Exception {
        mockMvc.perform(post("/api/stop"))
                .andExpect(status().isOk());

        verify(roverService).stop();
    }

    @Test
    void moveEndpointDispatchesDirection() throws Exception {
        mockMvc.perform(post("/api/move/forward"))
                .andExpect(status().isOk());

        verify(roverService).move(eq("forward"));
    }

    @Test
    void moveEndpointReturnsStructuredBadRequestForUnknownDirection() throws Exception {
        doThrow(new IllegalArgumentException("Unknown move command: spin")).when(roverService).move(eq("spin"));

        mockMvc.perform(post("/api/move/spin"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Unknown move command: spin"))
                .andExpect(jsonPath("$.path").value("/api/move/spin"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void preservesIncomingCorrelationIdHeader() throws Exception {
        mockMvc.perform(get("/api/status").header("X-Correlation-Id", "test-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "test-correlation-id"));
    }

    @Test
    void replacesBlankIncomingCorrelationIdHeader() throws Exception {
        String responseCorrelationId = mockMvc.perform(get("/api/status").header("X-Correlation-Id", "   "))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn()
                .getResponse()
                .getHeader("X-Correlation-Id");

        org.junit.jupiter.api.Assertions.assertNotNull(responseCorrelationId);
        org.junit.jupiter.api.Assertions.assertFalse(responseCorrelationId.isBlank());
        org.junit.jupiter.api.Assertions.assertNotEquals("   ", responseCorrelationId);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> UUID.fromString(responseCorrelationId));
    }

    @Test
    void frameEndpointReturnsServiceUnavailableWhenNoFrame() throws Exception {
        when(roverService.currentFrame()).thenReturn(null);

        mockMvc.perform(get("/api/frame"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void frameEndpointReturnsJpegWhenFrameExists() throws Exception {
        when(roverService.currentFrame()).thenReturn(new byte[] {1, 2, 3});

        mockMvc.perform(get("/api/frame"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
    }

    @Test
    void streamLoopsThroughNullFrameThenWritesFrame() throws Exception {
        RoverService service = mock(RoverService.class);
        when(service.currentFrame()).thenReturn(null, new byte[] {1, 2, 3});

        MockHttpServletResponse response = new MockHttpServletResponse() {
            @Override
            public ServletOutputStream getOutputStream() {
                return new ExplodingServletOutputStream();
            }
        };

        RoverController controller = new RoverController(service, 5000, 1);

        assertThrows(IOException.class, () -> controller.stream(response));
        verify(service, atLeast(2)).currentFrame();
        org.junit.jupiter.api.Assertions.assertEquals(
            "multipart/x-mixed-replace; boundary=frame",
            response.getContentType()
        );
    }

    @Test
    void streamReturnsImmediatelyWhenThreadAlreadyInterrupted() throws Exception {
        RoverService service = mock(RoverService.class);
        HttpServletResponse response = new MockHttpServletResponse();
        RoverController controller = new RoverController(service, 5000, 1);

        Thread.currentThread().interrupt();
        try {
            controller.stream(response);
        } finally {
            Thread.interrupted();
        }

        verify(service, never()).currentFrame();
    }

    @Test
    void streamWritesFrameAndStopsAfterFlushInterrupt() throws Exception {
        RoverService service = mock(RoverService.class);
        when(service.currentFrame()).thenReturn(new byte[] {1, 2, 3});

        MockHttpServletResponse response = new MockHttpServletResponse() {
            @Override
            public ServletOutputStream getOutputStream() {
                return new InterruptOnFlushServletOutputStream();
            }
        };

        RoverController controller = new RoverController(service, 5000, 1);

        controller.stream(response);

        verify(service, atLeastOnce()).currentFrame();
    }

    @Test
    void streamEndsWhenNoFrameArrivesBeforeIdleTimeout() throws Exception {
        RoverService service = mock(RoverService.class);
        when(service.currentFrame()).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RoverController(service, 20, 1).stream(response);

        verify(service, atLeast(2)).currentFrame();
        org.junit.jupiter.api.Assertions.assertEquals(0, response.getContentAsByteArray().length);
    }

    @Test
    void streamStopsWhenInterruptedWhileWaitingForFrame() throws Exception {
        RoverService service = mock(RoverService.class);
        when(service.currentFrame()).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return null;
        });
        RoverController controller = new RoverController(service, 5000, 1000);

        try {
            controller.stream(new MockHttpServletResponse());
            org.junit.jupiter.api.Assertions.assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }

        verify(service, times(1)).currentFrame();
    }

    static class ExplodingServletOutputStream extends ServletOutputStream {
        private int writes;

        @Override
        public void write(int b) throws IOException {
            writes++;
            if (writes > 20) {
                throw new IOException("stop stream");
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }
    }

    static class InterruptOnFlushServletOutputStream extends ServletOutputStream {
        @Override
        public void write(int b) {
        }

        @Override
        public void flush() {
            Thread.currentThread().interrupt();
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }
    }
}
