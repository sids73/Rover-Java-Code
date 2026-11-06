package com.rover.ac13.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets an external recognizer publish what it sees, and the web UI read it back
 * to draw boxes over the video. {@code POST} is protected by the API token like
 * the other mutating endpoints; {@code GET} is public.
 */
@RestController
@RequestMapping("/api/detections")
public class DetectionController {

    private final DetectionService detectionService;

    public DetectionController(DetectionService detectionService) {
        this.detectionService = detectionService;
    }

    @GetMapping
    public ResponseEntity<DetectionSnapshot> latest() {
        return detectionService.latest()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<Void> update(@RequestBody DetectionReport report) {
        detectionService.update(report);
        return ResponseEntity.noContent().build();
    }
}
