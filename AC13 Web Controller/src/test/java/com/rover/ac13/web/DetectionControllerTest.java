package com.rover.ac13.web;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DetectionController.class)
class DetectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DetectionService detectionService;

    @Test
    void getReturnsNoContentWhenNothingPosted() throws Exception {
        when(detectionService.latest()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/detections"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getReturnsLatestSnapshot() throws Exception {
        when(detectionService.latest()).thenReturn(Optional.of(new DetectionSnapshot(
                5, 640, 480, List.of(new Detection("person", 0.87, List.of(10, 20, 110, 220))),
                Instant.parse("2026-11-06T12:00:00Z"), 42)));

        mockMvc.perform(get("/api/detections"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.frame").value(5))
                .andExpect(jsonPath("$.width").value(640))
                .andExpect(jsonPath("$.height").value(480))
                .andExpect(jsonPath("$.ageMs").value(42))
                .andExpect(jsonPath("$.detections[0].label").value("person"))
                .andExpect(jsonPath("$.detections[0].score").value(0.87))
                .andExpect(jsonPath("$.detections[0].box[3]").value(220));
    }

    @Test
    void postStoresReportAndIgnoresUnknownFields() throws Exception {
        // Exactly what rover_detect.py sends, including its extra "time" field.
        String body = """
                {"time": 1793940646.969, "frame": 3, "width": 480, "height": 360,
                 "detections": [{"label": "cup", "score": 0.912, "box": [1, 2, 3, 4]}]}
                """;

        mockMvc.perform(post("/api/detections").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        ArgumentCaptor<DetectionReport> captor = ArgumentCaptor.forClass(DetectionReport.class);
        verify(detectionService).update(captor.capture());
        assertEquals(new DetectionReport(3, 480, 360,
                List.of(new Detection("cup", 0.912, List.of(1, 2, 3, 4)))), captor.getValue());
    }

    @Test
    void postReturnsStructuredBadRequestForInvalidReport() throws Exception {
        doThrow(new IllegalArgumentException("width and height must be positive"))
                .when(detectionService).update(any());

        mockMvc.perform(post("/api/detections").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frame\": 1, \"width\": 0, \"height\": 0, \"detections\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("width and height must be positive"))
                .andExpect(jsonPath("$.path").value("/api/detections"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void postReturnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/detections").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed request body"));

        verify(detectionService, never()).update(any());
    }
}
