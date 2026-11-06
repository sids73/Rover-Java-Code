package com.rover.ac13;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FrameExchangeTest {

    @Test
    void startsEmpty() {
        assertNull(new FrameExchange().latest());
    }

    @Test
    void publishCopiesTheGivenRangeAndNumbersFrames() {
        FrameExchange exchange = new FrameExchange();
        byte[] buffer = {9, 9, 1, 2, 3, 9};

        Frame first = exchange.publish(buffer, 2, 3);
        Frame second = exchange.publish(buffer, 0, 1);

        assertArrayEquals(new byte[] {1, 2, 3}, first.jpeg());
        assertEquals(3, first.length());
        assertEquals(1, first.sequence());
        assertEquals(2, second.sequence());
        assertSame(second, exchange.latest());
    }

    @Test
    void publishedFrameIsUnaffectedByLaterBufferWrites() {
        FrameExchange exchange = new FrameExchange();
        byte[] buffer = {1, 2, 3};

        Frame frame = exchange.publish(buffer, 0, 3);
        buffer[0] = 42;

        assertArrayEquals(new byte[] {1, 2, 3}, frame.jpeg());
    }

    @Test
    void readersCannotModifyTheSharedFrame() {
        Frame frame = new FrameExchange().publish(new byte[] {1, 2}, 0, 2);

        frame.jpeg()[0] = 42;

        assertArrayEquals(new byte[] {1, 2}, frame.jpeg());
    }

    @Test
    void publishRejectsEmptyFrames() {
        FrameExchange exchange = new FrameExchange();

        assertThrows(IllegalArgumentException.class, () -> exchange.publish(new byte[4], 0, 0));
    }

    @Test
    void awaitNewerReturnsCurrentFrameImmediatelyWhenItIsNewer() throws Exception {
        FrameExchange exchange = new FrameExchange();
        Frame frame = exchange.publish(new byte[] {1}, 0, 1);

        assertSame(frame, exchange.awaitNewer(0, 0, TimeUnit.MILLISECONDS));
    }

    @Test
    void awaitNewerTimesOutWhenNothingNewerArrives() throws Exception {
        FrameExchange exchange = new FrameExchange();
        Frame frame = exchange.publish(new byte[] {1}, 0, 1);

        assertNull(exchange.awaitNewer(frame.sequence(), 20, TimeUnit.MILLISECONDS));
        assertNull(new FrameExchange().awaitNewer(0, 20, TimeUnit.MILLISECONDS));
    }

    @Test
    void awaitNewerWakesWhenAFrameIsPublished() throws Exception {
        FrameExchange exchange = new FrameExchange();
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicReference<Frame> received = new AtomicReference<>();

        Thread reader = Thread.ofVirtual().start(() -> {
            try {
                waiting.countDown();
                received.set(exchange.awaitNewer(0, 5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiting.await();
        Frame published = exchange.publish(new byte[] {7}, 0, 1);
        reader.join(5000);

        assertSame(published, received.get());
    }

    @Test
    void severalReadersAllSeeTheSameFrame() throws Exception {
        FrameExchange exchange = new FrameExchange();
        Frame frame = exchange.publish(new byte[] {1}, 0, 1);

        // Reading does not consume: every reader gets the frame.
        assertSame(frame, exchange.awaitNewer(0, 0, TimeUnit.MILLISECONDS));
        assertSame(frame, exchange.awaitNewer(0, 0, TimeUnit.MILLISECONDS));
        assertSame(frame, exchange.latest());
    }

    @Test
    void clearDropsTheLatestFrameButKeepsSequenceIncreasing() throws Exception {
        FrameExchange exchange = new FrameExchange();
        Frame before = exchange.publish(new byte[] {1}, 0, 1);

        exchange.clear();

        assertNull(exchange.latest());
        assertNull(exchange.awaitNewer(0, 0, TimeUnit.MILLISECONDS));
        assertTrue(exchange.publish(new byte[] {2}, 0, 1).sequence() > before.sequence());
    }

    @Test
    void awaitNewerThrowsWhenInterrupted() {
        FrameExchange exchange = new FrameExchange();

        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> exchange.awaitNewer(0, 1, TimeUnit.SECONDS));
        } finally {
            Thread.interrupted();
        }
    }
}
