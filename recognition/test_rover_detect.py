"""Tests for the stream-handling parts of rover_detect (no torch or OpenCV needed).

Run with:  python -m unittest test_rover_detect
"""

import io
import threading
import unittest

from rover_detect import LatestFrame, StreamFormatError, iter_mjpeg_frames


def part(payload: bytes, boundary: bytes = b"frame") -> bytes:
    """One part exactly as RoverController.stream() writes it."""
    return (b"--" + boundary + b"\r\n"
            + b"Content-Type: image/jpeg\r\n"
            + b"Content-Length: " + str(len(payload)).encode() + b"\r\n\r\n"
            + payload + b"\r\n")


class IterMjpegFramesTest(unittest.TestCase):

    def test_yields_each_payload(self):
        body = part(b"\xff\xd8one\xff\xd9") + part(b"\xff\xd8two\xff\xd9")

        self.assertEqual(list(iter_mjpeg_frames(io.BytesIO(body))),
                         [b"\xff\xd8one\xff\xd9", b"\xff\xd8two\xff\xd9"])

    def test_payload_may_contain_crlf_and_boundary_text(self):
        tricky = b"\r\n--frame\r\nContent-Length: 1\r\n\r\n"

        self.assertEqual(list(iter_mjpeg_frames(io.BytesIO(part(tricky)))), [tricky])

    def test_empty_stream_yields_nothing(self):
        self.assertEqual(list(iter_mjpeg_frames(io.BytesIO(b""))), [])

    def test_stops_when_cut_off_mid_frame(self):
        body = part(b"complete") + part(b"truncated")[:-6]

        self.assertEqual(list(iter_mjpeg_frames(io.BytesIO(body))), [b"complete"])

    def test_stops_when_cut_off_in_headers(self):
        body = b"--frame\r\nContent-Type: image/jpeg\r\n"

        self.assertEqual(list(iter_mjpeg_frames(io.BytesIO(body))), [])

    def test_header_names_are_case_insensitive(self):
        body = b"--frame\r\ncontent-length: 3\r\n\r\nabc\r\n"

        self.assertEqual(list(iter_mjpeg_frames(io.BytesIO(body))), [b"abc"])

    def test_rejects_missing_boundary(self):
        with self.assertRaises(StreamFormatError):
            list(iter_mjpeg_frames(io.BytesIO(b"<html>not a stream</html>\r\n")))

    def test_rejects_part_without_content_length(self):
        with self.assertRaises(StreamFormatError):
            list(iter_mjpeg_frames(io.BytesIO(b"--frame\r\nContent-Type: image/jpeg\r\n\r\nabc\r\n")))

    def test_rejects_bad_content_length(self):
        with self.assertRaises(StreamFormatError):
            list(iter_mjpeg_frames(io.BytesIO(b"--frame\r\nContent-Length: lots\r\n\r\nabc\r\n")))


class LatestFrameTest(unittest.TestCase):

    def test_times_out_when_empty(self):
        self.assertIsNone(LatestFrame().wait_newer(0, timeout=0.01))

    def test_returns_only_frames_newer_than_given_sequence(self):
        frames = LatestFrame()
        frames.publish(b"a")
        frames.publish(b"b")

        self.assertEqual(frames.wait_newer(0, timeout=0), (2, b"b"))  # skips straight to newest
        self.assertIsNone(frames.wait_newer(2, timeout=0.01))

    def test_wakes_waiting_reader(self):
        frames = LatestFrame()
        result = []
        reader = threading.Thread(target=lambda: result.append(frames.wait_newer(0, timeout=5)))
        reader.start()

        frames.publish(b"jpeg")
        reader.join(5)

        self.assertEqual(result, [(1, b"jpeg")])


if __name__ == "__main__":
    unittest.main()
