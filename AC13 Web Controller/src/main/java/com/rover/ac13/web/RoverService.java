package com.rover.ac13.web;

import com.rover.ac13.AC13Comunicator;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class RoverService {

    private final Semaphore frameSemaphore;
    private final AC13Comunicator communicator;

    public RoverService() {
        this(new Semaphore(0));
    }

    RoverService(Semaphore frameSemaphore) {
        this(new AC13Comunicator(frameSemaphore), frameSemaphore);
    }

    RoverService(AC13Comunicator communicator, Semaphore frameSemaphore) {
        this.communicator = communicator;
        this.frameSemaphore = frameSemaphore;
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

    public byte[] currentFrame() {
        if (!communicator.isConnected() || !communicator.isStreaming()) {
            return null;
        }

        try {
            frameSemaphore.tryAcquire(300, TimeUnit.MILLISECONDS);
            byte[] frame = communicator.GetImageBuffer();
            if (frame == null || frame.length == 0) {
                return null;
            }
            return frame;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @PreDestroy
    public void shutdown() {
        communicator.close();
    }
}
