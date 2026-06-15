package com.rover.ac13.web;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
public class RoverController {

    private static final byte[] NEWLINE = "\r\n".getBytes();
    private static final String BOUNDARY = "frame";

    private final RoverService roverService;

    public RoverController(RoverService roverService) {
        this.roverService = roverService;
    }

    @GetMapping("/api/status")
    public Map<String, Object> status() {
        return roverService.status();
    }

    @PostMapping("/api/connect")
    public ResponseEntity<Map<String, Object>> connect() {
        if (roverService.connect()) {
            return ResponseEntity.ok(roverService.status());
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(roverService.status());
    }

    @PostMapping("/api/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect() {
        roverService.disconnect();
        return ResponseEntity.ok(roverService.status());
    }

    @PostMapping("/api/infrared/toggle")
    public ResponseEntity<Map<String, Object>> toggleInfrared() {
        roverService.toggleInfrared();
        return ResponseEntity.ok(roverService.status());
    }

    @PostMapping("/api/stop")
    public ResponseEntity<Void> stop() {
        roverService.stop();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/move/{direction}")
    public ResponseEntity<Void> move(@PathVariable("direction") String direction) {
        roverService.move(direction);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/api/frame", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> frame() {
        byte[] frame = roverService.currentFrame();
        if (frame == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(frame);
    }

    @GetMapping("/api/stream")
    public void stream(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType("multipart/x-mixed-replace; boundary=" + BOUNDARY);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");

        ServletOutputStream out = response.getOutputStream();

        while (!Thread.currentThread().isInterrupted()) {
            byte[] frame = roverService.currentFrame();
            if (frame == null) {
                continue;
            }

            out.write(("--" + BOUNDARY + "\r\n").getBytes());
            out.write("Content-Type: image/jpeg\r\n".getBytes());
            out.write(("Content-Length: " + frame.length + "\r\n\r\n").getBytes());
            out.write(frame);
            out.write(NEWLINE);
            out.flush();
        }
    }
}
