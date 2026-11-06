package com.rover.ac13.web;

import java.util.List;

/**
 * One recognized object in a camera frame.
 *
 * @param label object type, e.g. {@code "person"}
 * @param score confidence from 0 to 1
 * @param box   {@code [x1, y1, x2, y2]} in pixels of the analysed frame
 */
public record Detection(String label, double score, List<Integer> box) {
}
