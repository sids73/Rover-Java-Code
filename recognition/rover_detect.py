#!/usr/bin/env python3
"""Object detection on the AC13 rover's camera feed.

Reads the MJPEG stream served by the AC13 Web Controller (``GET /api/stream``),
runs a pre-trained COCO object detector from torchvision on the newest frame,
and shows the video with labelled boxes. Detections can also be printed as
JSON lines for other programs to consume.

Only read-only endpoints are used, so no API token is needed. Connect to the
rover first (web page or ``POST /api/connect``); until then the script waits
and keeps reconnecting to the stream.

Run ``python rover_detect.py --prepare`` once while you have internet access:
it downloads the model weights into the torch cache, so detection works
offline later while the computer is on the rover's Wi-Fi.
"""

from __future__ import annotations

import argparse
import json
import sys
import threading
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import BinaryIO, Iterator

MODELS = {
    # name: (torchvision builder, weights enum) - resolved lazily so the stream
    # code can be imported and tested without torch installed.
    "ssdlite": ("ssdlite320_mobilenet_v3_large", "SSDLite320_MobileNet_V3_Large_Weights"),
    "fasterrcnn": ("fasterrcnn_mobilenet_v3_large_fpn", "FasterRCNN_MobileNet_V3_Large_FPN_Weights"),
}


# --------------------------------------------------------------------------- #
# MJPEG stream
# --------------------------------------------------------------------------- #

class StreamFormatError(Exception):
    """The response is not the multipart MJPEG format /api/stream produces."""


def iter_mjpeg_frames(stream: BinaryIO) -> Iterator[bytes]:
    """Yield JPEG payloads from a ``multipart/x-mixed-replace`` body.

    Expects each part to carry a ``Content-Length`` header, as the AC13 Web
    Controller does. Returns when the stream ends cleanly between parts.
    """
    while True:
        line = stream.readline()
        if not line:
            return  # server closed the stream (e.g. idle timeout)
        line = line.strip()
        if not line:
            continue  # blank line between parts
        if not line.startswith(b"--"):
            raise StreamFormatError(f"expected boundary, got {line[:40]!r}")

        length = None
        while True:
            header = stream.readline()
            if not header:
                return
            header = header.strip()
            if not header:
                break  # end of part headers
            name, _, value = header.partition(b":")
            if name.strip().lower() == b"content-length":
                try:
                    length = int(value.strip())
                except ValueError as e:
                    raise StreamFormatError(f"bad Content-Length {value!r}") from e
        if length is None:
            raise StreamFormatError("part has no Content-Length header")

        payload = stream.read(length)
        if len(payload) < length:
            return  # stream cut off mid-frame
        yield payload


class LatestFrame:
    """Holds only the newest frame; readers wait for one newer than they last saw."""

    def __init__(self) -> None:
        self._cond = threading.Condition()
        self._frame: bytes | None = None
        self._sequence = 0

    def publish(self, jpeg: bytes) -> None:
        with self._cond:
            self._sequence += 1
            self._frame = jpeg
            self._cond.notify_all()

    def wait_newer(self, after: int, timeout: float) -> tuple[int, bytes] | None:
        with self._cond:
            if not self._cond.wait_for(lambda: self._sequence > after, timeout):
                return None
            assert self._frame is not None
            return self._sequence, self._frame


class StreamReader(threading.Thread):
    """Background thread that keeps reading /api/stream and reconnects when it ends."""

    def __init__(self, base_url: str, frames: LatestFrame, retry_delay: float = 1.0) -> None:
        super().__init__(name="mjpeg-reader", daemon=True)
        self.url = base_url.rstrip("/") + "/api/stream"
        self.frames = frames
        self.retry_delay = retry_delay
        self.status = "connecting"
        self._stop_event = threading.Event()

    def stop(self) -> None:
        self._stop_event.set()

    def run(self) -> None:
        while not self._stop_event.is_set():
            try:
                with urllib.request.urlopen(self.url, timeout=10) as response:
                    self.status = "waiting for frames"
                    for jpeg in iter_mjpeg_frames(response):
                        self.status = "streaming"
                        self.frames.publish(jpeg)
                        if self._stop_event.is_set():
                            return
                # The server ends idle streams (rover not connected): retry.
                self.status = "rover not streaming"
            except (urllib.error.URLError, OSError, StreamFormatError) as e:
                self.status = f"stream error: {e}"
            self._stop_event.wait(self.retry_delay)


# --------------------------------------------------------------------------- #
# Detection
# --------------------------------------------------------------------------- #

@dataclass
class Detection:
    label: str
    score: float
    box: tuple[int, int, int, int]  # x1, y1, x2, y2 in pixels

    def to_json(self) -> dict:
        return {"label": self.label, "score": round(self.score, 3), "box": list(self.box)}


class Detector:
    def __init__(self, model_name: str, device: str, threshold: float, classes: set[str] | None) -> None:
        import torch
        import torchvision.models.detection as detection

        builder_name, weights_name = MODELS[model_name]
        weights = getattr(detection, weights_name).DEFAULT
        self.model = getattr(detection, builder_name)(weights=weights).eval().to(device)
        self.transform = weights.transforms()
        self.categories: list[str] = weights.meta["categories"]
        self.device = device
        self.threshold = threshold
        self.classes = classes
        self._torch = torch

        unknown = (classes or set()) - set(self.categories)
        if unknown:
            raise SystemExit(f"Unknown class name(s): {', '.join(sorted(unknown))}. "
                             f"Use --list-classes to see the valid names.")

    def detect(self, rgb) -> list[Detection]:
        """Run the model on an RGB ``numpy`` image (H x W x 3, uint8)."""
        torch = self._torch
        tensor = torch.from_numpy(rgb).permute(2, 0, 1)  # HWC -> CHW
        with torch.inference_mode():
            output = self.model([self.transform(tensor).to(self.device)])[0]

        results = []
        for box, label, score in zip(output["boxes"], output["labels"], output["scores"]):
            score = float(score)
            name = self.categories[int(label)]
            if score < self.threshold or (self.classes and name not in self.classes):
                continue
            x1, y1, x2, y2 = (int(round(float(v))) for v in box)
            results.append(Detection(name, score, (x1, y1, x2, y2)))
        return results


def pick_device(requested: str) -> str:
    if requested != "auto":
        return requested
    import torch
    return "cuda" if torch.cuda.is_available() else "cpu"


# --------------------------------------------------------------------------- #
# Display
# --------------------------------------------------------------------------- #

def draw(cv2, bgr, detections: list[Detection], status: str) -> None:
    for d in detections:
        x1, y1, x2, y2 = d.box
        cv2.rectangle(bgr, (x1, y1), (x2, y2), (0, 220, 255), 2)
        text = f"{d.label} {d.score:.0%}"
        (tw, th), _ = cv2.getTextSize(text, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1)
        ty = max(y1, th + 6)
        cv2.rectangle(bgr, (x1, ty - th - 6), (x1 + tw + 6, ty), (0, 220, 255), -1)
        cv2.putText(bgr, text, (x1 + 3, ty - 4), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 1, cv2.LINE_AA)
    cv2.putText(bgr, status, (8, bgr.shape[0] - 8), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (255, 255, 255), 1, cv2.LINE_AA)


def placeholder(np, cv2, text: str):
    image = np.zeros((240, 320, 3), dtype=np.uint8)
    cv2.putText(image, text, (10, 125), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (200, 200, 200), 1, cv2.LINE_AA)
    return image


# --------------------------------------------------------------------------- #
# Main
# --------------------------------------------------------------------------- #

def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Detect objects in the AC13 rover's camera stream.")
    p.add_argument("--url", default="http://localhost:8080", help="AC13 Web Controller base URL (default: %(default)s)")
    p.add_argument("--model", choices=sorted(MODELS), default="ssdlite",
                   help="ssdlite is fast; fasterrcnn is slower but more accurate (default: %(default)s)")
    p.add_argument("--threshold", type=float, default=0.5, help="minimum confidence 0-1 (default: %(default)s)")
    p.add_argument("--classes", help="comma-separated COCO class names to keep, e.g. person,cup,chair")
    p.add_argument("--device", default="auto", help="torch device: auto, cpu, cuda or mps (default: %(default)s)")
    p.add_argument("--json", action="store_true", help="print one JSON line per processed frame to stdout")
    p.add_argument("--no-window", action="store_true", help="don't open a video window (use with --json)")
    p.add_argument("--prepare", action="store_true", help="download the model weights and exit")
    p.add_argument("--list-classes", action="store_true", help="print the class names the model knows and exit")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if not 0 <= args.threshold <= 1:
        raise SystemExit("--threshold must be between 0 and 1")
    classes = {c.strip() for c in args.classes.split(",") if c.strip()} if args.classes else None

    import cv2
    import numpy as np

    device = pick_device(args.device)
    print(f"Loading {args.model} on {device}...", file=sys.stderr)
    detector = Detector(args.model, device, args.threshold, classes)

    if args.list_classes:
        print("\n".join(c for c in detector.categories if c and c != "N/A" and c != "__background__"))
        return 0
    if args.prepare:
        print("Model weights are cached; detection will now work offline.", file=sys.stderr)
        return 0

    frames = LatestFrame()
    reader = StreamReader(args.url, frames)
    reader.start()
    print(f"Reading {reader.url} (press q or Esc in the window to quit)", file=sys.stderr)

    window = "AC13 rover - object detection"
    last_seq = 0
    fps = 0.0
    try:
        while True:
            got = frames.wait_newer(last_seq, timeout=0.5)
            if got is None:
                if not args.no_window:
                    cv2.imshow(window, placeholder(np, cv2, reader.status))
                    if cv2.waitKey(1) & 0xFF in (ord("q"), 27):
                        break
                continue

            last_seq, jpeg = got
            bgr = cv2.imdecode(np.frombuffer(jpeg, dtype=np.uint8), cv2.IMREAD_COLOR)
            if bgr is None:
                continue  # undecodable frame; wait for the next one

            started = time.perf_counter()
            detections = detector.detect(cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB))
            elapsed = time.perf_counter() - started
            fps = 1 / elapsed if fps == 0 else 0.9 * fps + 0.1 / elapsed

            if args.json:
                print(json.dumps({
                    "time": round(time.time(), 3),
                    "frame": last_seq,
                    "width": bgr.shape[1],
                    "height": bgr.shape[0],
                    "detections": [d.to_json() for d in detections],
                }), flush=True)

            if not args.no_window:
                draw(cv2, bgr, detections, f"{args.model} {fps:.1f} fps")
                cv2.imshow(window, bgr)
                if cv2.waitKey(1) & 0xFF in (ord("q"), 27):
                    break
    except KeyboardInterrupt:
        pass
    finally:
        reader.stop()
        if not args.no_window:
            cv2.destroyAllWindows()
    return 0


if __name__ == "__main__":
    sys.exit(main())
