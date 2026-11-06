package com.rover.ac13.web;

import com.rover.ac13.AC13Comunicator;
import com.rover.ac13.Frame;
import com.rover.ac13.FrameExchange;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RoverServiceTest {

    @Test
    void connectReturnsTrueWhenAlreadyConnected() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = true;
        RoverService service = new RoverService(fake);

        assertTrue(service.connect());
        assertEquals(0, fake.connectCalls);
    }

    @Test
    void connectDelegatesWhenDisconnected() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = false;
        fake.connectResult = false;
        RoverService service = new RoverService(fake);

        assertFalse(service.connect());
        assertEquals(1, fake.connectCalls);
    }

    @Test
    void disconnectReturnsTrueWhenAlreadyDisconnected() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = false;
        RoverService service = new RoverService(fake);

        assertTrue(service.disconnect());
        assertEquals(0, fake.disconnectCalls);
    }

    @Test
    void disconnectDelegatesWhenConnected() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = true;
        fake.disconnectResult = false;
        RoverService service = new RoverService(fake);

        assertFalse(service.disconnect());
        assertEquals(1, fake.disconnectCalls);
    }

    @Test
    void moveDispatchesToCorrectCommands() {
        FakeComunicator fake = new FakeComunicator();
        RoverService service = new RoverService(fake);

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
        RoverService service = new RoverService(fake);

        assertThrows(IllegalArgumentException.class, () -> service.move("spin"));
    }

    @Test
    void statusReflectsCommunicatorState() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = true;
        fake.streaming = true;
        fake.infrared = "On";
        RoverService service = new RoverService(fake);

        var status = service.status();

        assertEquals(true, status.get("connected"));
        assertEquals(true, status.get("streaming"));
        assertEquals("On", status.get("infrared"));
    }

    @Test
    void toggleInfraredDelegates() {
        FakeComunicator fake = new FakeComunicator();
        RoverService service = new RoverService(fake);

        service.toggleInfrared();

        assertEquals("On", fake.infrared);
    }

    @Test
    void stopDelegates() {
        FakeComunicator fake = new FakeComunicator();
        RoverService service = new RoverService(fake);

        service.stop();

        assertEquals(1, fake.stopCalls);
    }

    @Test
    void currentFrameReturnsNullWhenDisconnected() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = false;
        fake.streaming = true;
        fake.frames.publish(new byte[] {1, 2, 3}, 0, 3);
        RoverService service = new RoverService(fake);

        assertNull(service.currentFrame());
    }

    @Test
    void currentFrameReturnsNullWhenNotStreaming() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = true;
        fake.streaming = false;
        fake.frames.publish(new byte[] {1, 2, 3}, 0, 3);
        RoverService service = new RoverService(fake);

        assertNull(service.currentFrame());
    }

    @Test
    void currentFrameReturnsNullBeforeFirstFrame() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = true;
        fake.streaming = true;
        RoverService service = new RoverService(fake);

        assertNull(service.currentFrame());
    }

    @Test
    void currentFrameReturnsLatestFrameWhenConnectedAndStreaming() {
        FakeComunicator fake = new FakeComunicator();
        fake.connected = true;
        fake.streaming = true;
        fake.frames.publish(new byte[] {1, 2, 3}, 0, 3);
        fake.frames.publish(new byte[] {4, 5}, 0, 2);
        RoverService service = new RoverService(fake);

        assertArrayEquals(new byte[] {4, 5}, service.currentFrame());
        // Reading does not consume the frame.
        assertArrayEquals(new byte[] {4, 5}, service.currentFrame());
    }

    @Test
    void nextFrameReturnsNewerFrameAndTimesOutOtherwise() throws Exception {
        FakeComunicator fake = new FakeComunicator();
        Frame frame = fake.frames.publish(new byte[] {1}, 0, 1);
        RoverService service = new RoverService(fake);

        assertSame(frame, service.nextFrame(0, 0));
        assertNull(service.nextFrame(frame.sequence(), 10));
    }

    @Test
    void shutdownClosesCommunicator() {
        FakeComunicator fake = new FakeComunicator();
        RoverService service = new RoverService(fake);

        service.shutdown();

        assertEquals(1, fake.closeCalls);
    }

    static class FakeComunicator extends AC13Comunicator {
        boolean connected;
        boolean streaming;
        String infrared = "Off";
        final FrameExchange frames = new FrameExchange();

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
        public Frame latestFrame() {
            return frames.latest();
        }

        @Override
        public Frame awaitFrame(long afterSequence, long timeout, TimeUnit unit) throws InterruptedException {
            return frames.awaitNewer(afterSequence, timeout, unit);
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }
}
