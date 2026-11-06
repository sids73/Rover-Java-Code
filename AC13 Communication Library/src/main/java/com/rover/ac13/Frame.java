package com.rover.ac13;

/**
 * One complete JPEG frame from the rover camera, with a sequence number that
 * increases by one for every frame published by a {@link FrameExchange}.
 * Instances are immutable and safe to share between threads.
 */
public final class Frame {

	private final long sequence;
	private final byte[] jpeg;

	/** Takes ownership of {@code jpeg}; callers must not modify it afterwards. */
	Frame(long sequence, byte[] jpeg) {
		this.sequence = sequence;
		this.jpeg = jpeg;
	}

	/** Position of this frame in the stream; later frames have larger numbers. */
	public long sequence() {
		return sequence;
	}

	/** Size of the JPEG data in bytes. */
	public int length() {
		return jpeg.length;
	}

	/** A copy of the JPEG data, so readers cannot affect each other. */
	public byte[] jpeg() {
		return jpeg.clone();
	}
}
