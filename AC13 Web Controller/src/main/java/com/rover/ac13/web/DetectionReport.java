package com.rover.ac13.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Detections for one frame, as posted by an external recognizer such as
 * {@code recognition/rover_detect.py}. Unknown fields (e.g. the script's
 * {@code time}) are ignored.
 *
 * @param frame      the recognizer's frame counter
 * @param width      width of the analysed frame in pixels
 * @param height     height of the analysed frame in pixels
 * @param detections objects found in the frame; may be empty
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DetectionReport(long frame, int width, int height, List<Detection> detections) {
}
