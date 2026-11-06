package com.rover.ac13.web;

import java.time.Instant;
import java.util.List;

/**
 * The latest detection report as served to the web UI.
 *
 * @param ageMs milliseconds since the report was received, measured by the server so
 *              clients can tell stale results apart without comparing clocks
 */
public record DetectionSnapshot(
        long frame,
        int width,
        int height,
        List<Detection> detections,
        Instant receivedAt,
        long ageMs
) {
}
