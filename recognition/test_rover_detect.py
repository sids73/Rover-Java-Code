"""Tests for rover_detect that don't need torch or OpenCV.

The stream tests use only the standard library. The JPEG decoding tests need
Pillow and NumPy and are skipped when those aren't installed.

Run with:  python -m unittest test_rover_detect
"""

import importlib.util
import io
import os
import tempfile
import threading
import unittest

from rover_detect import LatestFrame, StreamFormatError, decode_jpeg, iter_mjpeg_frames

HAVE_PIL_AND_NUMPY = all(importlib.util.find_spec(m) for m in ("PIL", "numpy"))


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


@unittest.skipUnless(HAVE_PIL_AND_NUMPY, "needs Pillow and NumPy")
class DecodeJpegTest(unittest.TestCase):

    @staticmethod
    def jpeg(padding: int = 0) -> bytes:
        """A small JPEG, optionally padded before its EOI marker like the rover's frames."""
        import numpy as np
        from PIL import Image

        pixels = np.random.default_rng(1).integers(0, 255, (48, 64, 3), dtype=np.uint8)
        out = io.BytesIO()
        Image.fromarray(pixels).save(out, format="JPEG")
        data = out.getvalue()
        assert data.endswith(b"\xff\xd9")
        return data[:-2] + b"\x5a" * padding + b"\xff\xd9"

    @staticmethod
    def native_stderr_of(fn):
        """Run fn, capturing what C code writes to file descriptor 2 (libjpeg warnings go there)."""
        with tempfile.TemporaryFile() as captured:
            saved = os.dup(2)
            os.dup2(captured.fileno(), 2)
            try:
                result = fn()
            finally:
                os.dup2(saved, 2)
                os.close(saved)
            captured.seek(0)
            return result, captured.read()

    def test_decodes_to_rgb_array(self):
        rgb = decode_jpeg(self.jpeg())

        self.assertEqual(rgb.shape, (48, 64, 3))

    def test_padded_frame_decodes_without_libjpeg_warning(self):
        rgb, stderr = self.native_stderr_of(lambda: decode_jpeg(self.jpeg(padding=8)))

        self.assertEqual(rgb.shape, (48, 64, 3))
        self.assertEqual(stderr, b"")

    def test_returns_none_for_non_jpeg_data(self):
        self.assertIsNone(decode_jpeg(b"not a jpeg"))

    def test_returns_none_for_truncated_jpeg(self):
        self.assertIsNone(decode_jpeg(self.jpeg()[:200]))


if __name__ == "__main__":
    unittest.main()
