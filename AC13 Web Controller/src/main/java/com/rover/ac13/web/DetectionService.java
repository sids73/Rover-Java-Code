package com.rover.ac13.web;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the most recent detection report posted by an external recognizer.
 * Only the latest report matters for the overlay, so older ones are replaced.
 */
@Service
public class DetectionService {

    static final int MAX_DETECTIONS = 100;

    private record Stored(DetectionReport report, Instant receivedAt) {
    }

    private final Clock clock;
    private final AtomicReference<Stored> latest = new AtomicReference<>();

    public DetectionService() {
        this(Clock.systemUTC());
    }

    DetectionService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Validates and stores {@code report} as the latest one.
     *
     * @throws IllegalArgumentException if the report is malformed
     */
    public void update(DetectionReport report) {
        validate(report);
        latest.set(new Stored(
                new DetectionReport(report.frame(), report.width(), report.height(), List.copyOf(report.detections())),
                clock.instant()));
    }

    /** The latest report with its age, or empty if none has been posted. */
    public Optional<DetectionSnapshot> latest() {
        Stored stored = latest.get();
        if (stored == null) {
            return Optional.empty();
        }
        DetectionReport report = stored.report();
        long ageMs = Duration.between(stored.receivedAt(), clock.instant()).toMillis();
        return Optional.of(new DetectionSnapshot(
                report.frame(), report.width(), report.height(), report.detections(),
                stored.receivedAt(), ageMs));
    }

    private static void validate(DetectionReport report) {
        if (report.width() <= 0 || report.height() <= 0) {
            throw new IllegalArgumentException("width and height must be positive");
        }
        List<Detection> detections = report.detections();
        if (detections == null) {
            throw new IllegalArgumentException("detections is required");
        }
        if (detections.size() > MAX_DETECTIONS) {
            throw new IllegalArgumentException("at most " + MAX_DETECTIONS + " detections per report");
        }
        for (Detection detection : detections) {
            if (detection == null) {
                throw new IllegalArgumentException("detections must not contain null");
            }
            if (detection.label() == null || detection.label().isBlank()) {
                throw new IllegalArgumentException("detection label is required");
            }
            if (!(detection.score() >= 0 && detection.score() <= 1)) {
                throw new IllegalArgumentException("detection score must be between 0 and 1");
            }
            // anyMatch rather than contains(null): immutable lists throw on contains(null).
            if (detection.box() == null || detection.box().size() != 4
                    || detection.box().stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("detection box must be [x1, y1, x2, y2]");
            }
        }
    }
}
