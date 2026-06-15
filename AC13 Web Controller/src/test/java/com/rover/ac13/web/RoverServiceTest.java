package com.rover.ac13.web;

import com.rover.ac13.AC13Comunicator;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;

class RoverServiceTest {

    @Test
    void connectReturnsTrueWhenAlreadyConnected() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = true;
        RoverService service = new RoverService(fake, new Semaphore(0));

        assertTrue(service.connect());
        assertEquals(0, fake.connectCalls);
    }

    @Test
    void connectDelegatesWhenDisconnected() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = false;
        fake.connectResult = false;
        RoverService service = new RoverService(fake, new Semaphore(0));

        assertFalse(service.connect());
        assertEquals(1, fake.connectCalls);
    }

    @Test
    void disconnectReturnsTrueWhenAlreadyDisconnected() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = false;
        RoverService service = new RoverService(fake, new Semaphore(0));

        assertTrue(service.disconnect());
        assertEquals(0, fake.disconnectCalls);
    }

    @Test
    void disconnectDelegatesWhenConnected() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = true;
        fake.disconnectResult = false;
        RoverService service = new RoverService(fake, new Semaphore(0));

        assertFalse(service.disconnect());
        assertEquals(1, fake.disconnectCalls);
    }

    @Test
    void moveDispatchesToCorrectCommands() {
        FakeComunicator fake = new FakeComunicator();
        RoverService service = new RoverService(fake, new Semaphore(0));

        service.move("forward");
        service.move("backward");
        service.move("left");
        service.move("right");

        assertEquals(1, fake.forwardCalls);
        assertEquals(1, fake.backwardCalls);
        assertEquals(1, fake.leftCalls);
        assertEquals(1, fake.rightCalls);
    }

    @Test
    void moveRejectsUnknownCommand() {
        FakeComunicator fake = new FakeComunicator();
        RoverService service = new RoverService(fake, new Semaphore(0));

        assertThrows(IllegalArgumentException.class, () -> service.move("spin"));
    }

    @Test
    void statusReflectsCommunicatorState() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = true;
        fake.streaming = true;
        fake.infrared = "On";
        RoverService service = new RoverService(fake, new Semaphore(0));

        var status = service.status();

        assertEquals(true, status.get("connected"));
        assertEquals(true, status.get("streaming"));
        assertEquals("On", status.get("infrared"));
    }

    @Test
    void toggleInfraredDelegates() {
        FakeComunicator fake = new FakeComunicator();
        RoverService service = new RoverService(fake, new Semaphore(0));

        service.toggleInfrared();

        assertEquals("On", fake.infrared);
    }

    @Test
    void stopDelegates() {
        FakeComunicator fake = new FakeComunicator();
        RoverService service = new RoverService(fake, new Semaphore(0));

        service.stop();

        assertEquals(1, fake.stopCalls);
    }

    @Test
    void currentFrameReturnsNullWhenDisconnected() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = false;
        fake.streaming = true;
        fake.frame = new byte[] {1, 2, 3};
        RoverService service = new RoverService(fake, new Semaphore(0));

        assertNull(service.currentFrame());
    }

    @Test
    void currentFrameReturnsNullWhenNotStreaming() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = true;
        fake.streaming = false;
        fake.frame = new byte[] {1, 2, 3};
        RoverService service = new RoverService(fake, new Semaphore(1));

        assertNull(service.currentFrame());
    }

    @Test
    void currentFrameReturnsNullWhenBufferIsNull() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = true;
        fake.streaming = true;
        fake.frame = null;
        RoverService service = new RoverService(fake, new Semaphore(1));

        assertNull(service.currentFrame());
    }

    @Test
    void currentFrameReturnsImageWhenConnectedAndStreaming() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = true;
        fake.streaming = true;
        fake.frame = new byte[] {1, 2, 3};
        RoverService service = new RoverService(fake, new Semaphore(1));

        byte[] frame = service.currentFrame();

        assertNotNull(frame);
        assertArrayEquals(new byte[] {1, 2, 3}, frame);
    }

    @Test
    void currentFrameReturnsNullForEmptyBuffer() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = true;
        fake.streaming = true;
        fake.frame = new byte[0];
        RoverService service = new RoverService(fake, new Semaphore(1));

        assertNull(service.currentFrame());
    }

    @Test
    void currentFrameReturnsNullWhenFrameReadThrows() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = true;
        fake.streaming = true;
        fake.throwOnGetImage = true;
        RoverService service = new RoverService(fake, new Semaphore(1));

        assertNull(service.currentFrame());
    }

    @Test
    void currentFrameReturnsNullWhenInterrupted() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = true;
        fake.streaming = true;
        fake.frame = new byte[] {1};
        RoverService service = new RoverService(fake, new Semaphore(0));

        Thread.currentThread().interrupt();
        try {
            assertNull(service.currentFrame());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shutdownClosesCommunicator() {
        FakeComunicator fake = new FakeComunicator();
        RoverService service = new RoverService(fake, new Semaphore(0));

        service.shutdown();

        assertEquals(1, fake.closeCalls);
    }

    static class FakeComunicator extends AC13Comunicator {
        boolean connected;
        boolean streaming;
        String infrared = "Off";
        byte[] frame;

        int connectCalls;
        int disconnectCalls;
        int closeCalls;
        int stopCalls;
        int forwardCalls;
        int backwardCalls;
        int leftCalls;
        int rightCalls;
        boolean connectResult = true;
        boolean disconnectResult = true;
        boolean throwOnGetImage;

        @Override
        public synchronized boolean Connect() {
            connectCalls++;
            connected = connectResult;
            return connectResult;
        }

        @Override
        public synchronized boolean Disconnect() {
            disconnectCalls++;
            connected = false;
            streaming = false;
            return disconnectResult;
        }

        @Override
        public synchronized boolean isConnected() {
            return connected;
        }

        @Override
        public boolean isStreaming() {
            return streaming;
        }

        @Override
        public String getInfraredState() {
            return infrared;
        }

        @Override
        public void SwitchInfrared() {
            infrared = infrared.equals("Off") ? "On" : "Off";
        }

        @Override
        public void MoveFoward() {
            forwardCalls++;
        }

        @Override
        public void MoveBackward() {
            backwardCalls++;
        }

        @Override
        public void RotateLeft() {
            leftCalls++;
        }

        @Override
        public void RotateRight() {
            rightCalls++;
        }

        @Override
        public void stopMovement() {
            stopCalls++;
        }

        @Override
        public byte[] GetImageBuffer() {
            if (throwOnGetImage) {
                throw new RuntimeException("boom");
            }
            return frame;
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }
}
