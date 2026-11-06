package com.rover.ac13.web;

import com.rover.ac13.AC13Comunicator;
import com.rover.ac13.Frame;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class RoverService {

    private final AC13Comunicator communicator;

    public RoverService() {
        this(new AC13Comunicator());
    }

    RoverService(AC13Comunicator communicator) {
        this.communicator = communicator;
    }

    public synchronized boolean connect() {
        if (communicator.isConnected()) {
            return true;
        }
        return communicator.Connect();
    }

    public synchronized boolean disconnect() {
        if (!communicator.isConnected()) {
            return true;
        }
        return communicator.Disconnect();
    }

    public synchronized Map<String, Object> status() {
        return Map.of(
                "connected", communicator.isConnected(),
                "streaming", communicator.isStreaming(),
                "infrared", communicator.getInfraredState()
        );
    }

    public synchronized void toggleInfrared() {
        communicator.SwitchInfrared();
    }

    public synchronized void move(String command) {
        switch (command) {
            case "forward" -> communicator.MoveFoward();
            case "backward" -> communicator.MoveBackward();
            case "left" -> communicator.RotateLeft();
            case "right" -> communicator.RotateRight();
            default -> throw new IllegalArgumentException("Unknown move command: " + command);
        }
    }

    public synchronized void stop() {
        communicator.stopMovement();
    }

    /**
     * The newest frame's JPEG bytes, or {@code null} when the rover is not connected
     * and streaming or no frame has arrived yet. Does not wait.
     */
    public byte[] currentFrame() {
        if (!communicator.isConnected() || !communicator.isStreaming()) {
            return null;
        }
        Frame frame = communicator.latestFrame();
        return frame == null ? null : frame.jpeg();
    }

    /**
     * Waits up to {@code timeoutMillis} for a frame newer than {@code afterSequence}
     * (pass {@code 0} for the current one). Any number of callers can do this at once
     * without taking frames from each other.
     *
     * @return the newer frame, or {@code null} on timeout
     */
    public Frame nextFrame(long afterSequence, long timeoutMillis) throws InterruptedException {
        return communicator.awaitFrame(afterSequence, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        communicator.close();
    }
}
