package com.rover.ac13;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Hands the most recent camera frame from the single streaming thread to any
 * number of readers.
 *
 * <p>The producer copies each finished frame out of its working buffer when it
 * {@link #publish publishes} it, so readers never see a frame that is being
 * overwritten. Readers do not consume anything: each one remembers the
 * {@link Frame#sequence() sequence} of the last frame it handled and asks for a
 * newer one, so a video stream, a recognizer and a UI can all read the same
 * frames independently. Only the latest frame is kept; a slow reader skips
 * frames instead of building up a backlog.
 */
public final class FrameExchange {

	private final ReentrantLock lock = new ReentrantLock();
	private final Condition published = lock.newCondition();

	private long nextSequence = 1;
	private Frame latest;

	/**
	 * Publishes a copy of {@code length} bytes of {@code buffer} starting at
	 * {@code offset} as the newest frame, and wakes all waiting readers.
	 *
	 * @return the published frame
	 */
	public Frame publish(byte[] buffer, int offset, int length) {
		if (length <= 0) {
			throw new IllegalArgumentException("Frame length must be positive: " + length);
		}
		Frame frame;
		byte[] copy = Arrays.copyOfRange(buffer, offset, offset + length);
		lock.lock();
		try {
			frame = new Frame(nextSequence++, copy);
			latest = frame;
			published.signalAll();
		} finally {
			lock.unlock();
		}
		return frame;
	}

	/** The newest frame, or {@code null} if none has been published since the last {@link #clear()}. */
	public Frame latest() {
		lock.lock();
		try {
			return latest;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Returns the newest frame if its sequence is greater than {@code afterSequence},
	 * waiting up to {@code timeout} for one to be published.
	 *
	 * <p>Pass {@code 0} to get the current frame straight away if there is one.
	 *
	 * @return a frame newer than {@code afterSequence}, or {@code null} on timeout
	 * @throws InterruptedException if the calling thread is interrupted while waiting
	 */
	public Frame awaitNewer(long afterSequence, long timeout, TimeUnit unit) throws InterruptedException {
		long remaining = unit.toNanos(timeout);
		lock.lockInterruptibly();
		try {
			while (latest == null || latest.sequence() <= afterSequence) {
				if (remaining <= 0) {
					return null;
				}
				remaining = published.awaitNanos(remaining);
			}
			return latest;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Drops the latest frame, e.g. on disconnect, so readers do not get a stale
	 * frame from an earlier session. Sequence numbers keep increasing.
	 */
	public void clear() {
		lock.lock();
		try {
			latest = null;
		} finally {
			lock.unlock();
		}
	}
}
