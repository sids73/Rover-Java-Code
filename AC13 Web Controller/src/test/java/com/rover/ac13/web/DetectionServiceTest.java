package com.rover.ac13.web;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DetectionServiceTest {

    /** A clock the test can move forward. */
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-06T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static Detection person() {
        return new Detection("person", 0.9, List.of(1, 2, 30, 40));
    }

    private static DetectionReport report(Detection... detections) {
        return new DetectionReport(7, 640, 480, Arrays.asList(detections));
    }

    @Test
    void latestIsEmptyBeforeAnyReport() {
        assertTrue(new DetectionService().latest().isEmpty());
    }

    @Test
    void storesReportWithReceiveTimeAndAge() {
        MutableClock clock = new MutableClock();
        DetectionService service = new DetectionService(clock);
        Instant received = clock.instant();

        service.update(report(person()));
        clock.advance(Duration.ofMillis(250));

        DetectionSnapshot snapshot = service.latest().orElseThrow();
        assertEquals(7, snapshot.frame());
        assertEquals(640, snapshot.width());
        assertEquals(480, snapshot.height());
        assertEquals(List.of(person()), snapshot.detections());
        assertEquals(received, snapshot.receivedAt());
        assertEquals(250, snapshot.ageMs());
    }

    @Test
    void newerReportReplacesOlder() {
        DetectionService service = new DetectionService();

        service.update(report(person()));
        service.update(new DetectionReport(8, 640, 480, List.of()));

        DetectionSnapshot snapshot = service.latest().orElseThrow();
        assertEquals(8, snapshot.frame());
        assertTrue(snapshot.detections().isEmpty());
    }

    @Test
    void storedDetectionsAreACopy() {
        DetectionService service = new DetectionService();
        List<Detection> detections = new ArrayList<>(List.of(person()));

        service.update(new DetectionReport(1, 640, 480, detections));
        detections.clear();

        assertEquals(1, service.latest().orElseThrow().detections().size());
    }

    @Test
    void rejectsNonPositiveSize() {
        DetectionService service = new DetectionService();

        assertRejected(service, new DetectionReport(1, 0, 480, List.of()), "width and height must be positive");
        assertRejected(service, new DetectionReport(1, 640, -1, List.of()), "width and height must be positive");
    }

    @Test
    void rejectsMissingDetections() {
        assertRejected(new DetectionService(), new DetectionReport(1, 640, 480, null), "detections is required");
    }

    @Test
    void rejectsTooManyDetections() {
        List<Detection> many = Collections.nCopies(DetectionService.MAX_DETECTIONS + 1, person());

        assertRejected(new DetectionService(), new DetectionReport(1, 640, 480, many), "at most 100 detections per report");
    }

    @Test
    void acceptsTheMaximumNumberOfDetections() {
        DetectionService service = new DetectionService();

        service.update(new DetectionReport(1, 640, 480, Collections.nCopies(DetectionService.MAX_DETECTIONS, person())));

        assertEquals(DetectionService.MAX_DETECTIONS, service.latest().orElseThrow().detections().size());
    }

    @Test
    void rejectsNullDetection() {
        assertRejected(new DetectionService(), report((Detection) null), "detections must not contain null");
    }

    @Test
    void rejectsMissingOrBlankLabel() {
        DetectionService service = new DetectionService();

        assertRejected(service, report(new Detection(null, 0.5, List.of(0, 0, 1, 1))), "detection label is required");
        assertRejected(service, report(new Detection(" ", 0.5, List.of(0, 0, 1, 1))), "detection label is required");
    }

    @Test
    void rejectsScoreOutsideZeroToOne() {
        DetectionService service = new DetectionService();
        String message = "detection score must be between 0 and 1";

        assertRejected(service, report(new Detection("cup", -0.1, List.of(0, 0, 1, 1))), message);
        assertRejected(service, report(new Detection("cup", 1.5, List.of(0, 0, 1, 1))), message);
        assertRejected(service, report(new Detection("cup", Double.NaN, List.of(0, 0, 1, 1))), message);
    }

    @Test
    void acceptsScoreBoundaries() {
        DetectionService service = new DetectionService();

        service.update(report(new Detection("cup", 0, List.of(0, 0, 1, 1)), new Detection("cup", 1, List.of(0, 0, 1, 1))));

        assertEquals(2, service.latest().orElseThrow().detections().size());
    }

    @Test
    void rejectsMalformedBox() {
        DetectionService service = new DetectionService();
        String message = "detection box must be [x1, y1, x2, y2]";

        assertRejected(service, report(new Detection("cup", 0.5, null)), message);
        assertRejected(service, report(new Detection("cup", 0.5, List.of(1, 2, 3))), message);
        assertRejected(service, report(new Detection("cup", 0.5, Arrays.asList(1, 2, null, 4))), message);
    }

    private static void assertRejected(DetectionService service, DetectionReport report, String message) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.update(report));
        assertEquals(message, ex.getMessage());
        assertTrue(service.latest().isEmpty(), "rejected report must not be stored");
    }
}
