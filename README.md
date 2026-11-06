# Rover Java Code

Modernized rover controller workspace with a shared communication library, a desktop controller, and a Spring Boot web controller.

## How It Works

The project drives an AC13 Wi-Fi rover: a small tracked robot with a camera and an infrared light. It has its own Wi-Fi network and listens at `192.168.1.100:80`. Your computer joins that network, opens TCP connections to the rover, sends it binary drive commands, and reads back a stream of JPEG frames from the camera.

```
┌──────────────────────┐     ┌──────────────────────┐
│ AC13 Desktop         │     │ AC13 Web Controller  │◀── browser (index.html)
│ Controller (Swing)   │     │ (Spring Boot, :8080) │      REST + MJPEG stream
└──────────┬───────────┘     └──────────┬───────────┘
           │        uses                │
           └──────────────┬─────────────┘
                          ▼
             ┌──────────────────────────┐
             │ AC13 Communication       │
             │ Library (AC13Comunicator)│
             └────────────┬─────────────┘
                          │ TCP + HTTP over the rover's Wi-Fi
                          ▼
                 AC13 rover @ 192.168.1.100:80
```

### Modules

| Module | Artifact | Purpose |
|---|---|---|
| `AC13 Communication Library` | `ac13-communicator-library` | Plain Java library that speaks the rover's protocol. It has no dependencies. |
| `AC13 Desktop Controller` | `ac13-desktop-controller` | Swing desktop app. Shows the video feed and has arrow buttons and an infrared switch. |
| `AC13 Web Controller` | `ac13-web-controller` | Spring Boot app. Offers the same controls as a REST API and a browser page. |
| `recognition/` | (Python, not Maven) | Object detection on the camera stream. See [Image Recognition](#image-recognition). |

The root `pom.xml` is the parent of all three modules. It sets Java 25, the Surefire setup, and the `quality` and `coverage` profiles.

### Communication Library (`AC13Comunicator`)

[`AC13Comunicator.java`](AC13%20Communication%20Library/src/main/java/com/rover/ac13/AC13Comunicator.java) does all the talking to the rover. It is `AutoCloseable`, and `close()` disconnects.

**Connection handshake (`Connect()`)**

1. Opens a TCP socket to the rover and sends an HTTP-style `GET /check_user.cgi?user=AC13&pwd=AC13` login request. Then it closes that socket.
2. Opens a new **command socket** (`cSock`) and sends binary commands 1, 2 and 3. Command 2 carries the `AC13` credentials. Bytes 25–28 of the reply to command 3 are an image ID.
3. Opens a **video socket** (`vSock`) and sends command 4 with that image ID. This asks the rover to start streaming video.
4. Starts a virtual thread (`rover-video-stream`) that keeps reading frames, then sets `isConnected = true`.

If any step throws an exception, the library calls `Disconnect()` and `Connect()` returns `false`.

**Command packets (`WriteCmd`)**

Every command is a short byte array that starts with the magic header `MO_O`. Command 4 is the exception: it starts with `MO_V` and goes over the video socket. The rest of the bytes say what the command does:

| Index | Purpose |
|---|---|
| 1–3 | Handshake (login and session setup) |
| 4 | Start the video stream (video socket) |
| 5, 6, 7, 8 | Drive one wheel/track forward or backward (25-byte packet) |
| 10 / 11 | Infrared on / off |
| 9 | Unused (marked "IR off(?)" in the code) |

The high-level movement methods combine the wheel commands:

- `MoveFoward()` sends 5 and 7, and `MoveBackward()` sends 6 and 8.
- `RotateLeft()` and `RotateRight()` drive one side forward and the other backward, so the rover turns on the spot.
- `stopMovement()` takes the last command sent, changes bytes 23 and 24 to stop codes, and sends it again.

The rover keeps moving until it gets a stop. That's why both UIs send a move command when you press a control and a stop when you release it.

**Video streaming**

The rover sends each JPEG frame with a 36-byte header that starts with `MO_V`, often split across several TCP packets. The streaming thread reads into a buffer of about 230 KB (`imageBuffer`). When it sees the `MO_V` header of the next frame, it knows the previous frame is complete. It then:

- copies that frame out of the buffer, without the 36-byte header, and publishes the copy to a `FrameExchange`;
- moves back to the start of the buffer once it is more than half full.

**Sharing frames (`FrameExchange` and `Frame`)**

`FrameExchange` keeps only the newest frame. Each `Frame` holds its own copy of the JPEG bytes and a sequence number that goes up by one for every new frame. Because the copy is taken as soon as the frame is complete, readers never see a frame the streaming thread is still overwriting.

Reading a frame doesn't use it up, so any number of readers can share the same frames: the video stream, a desktop window, or a future image recognizer. Each reader remembers the sequence number of the last frame it handled:

- `latestFrame()` returns the newest frame straight away, or `null` if there isn't one yet.
- `awaitFrame(afterSequence, timeout, unit)` waits until a frame newer than `afterSequence` arrives, or returns `null` when the timeout runs out. Pass `0` to get the current frame immediately.

A reader that falls behind simply skips to the newest frame instead of building up a backlog. Disconnecting clears the latest frame, so a stale image from the last session is never shown.

**HTTP CGI calls**

A few settings use the rover's built-in web interface (Basic auth `AC13:AC13`) through `java.net.http.HttpClient`:

- `SwitchTo640X480Resolution()` / `SwitchTo320X240Resolution()` call `/set_params.cgi?resolution=32` or `=8`.
- `GetAllParameters()` calls `/get_params.cgi`, parses the `key='value';` lines, and stores them. `getParameters_List()` returns them as text.

Both run on virtual threads so they don't block the caller.

> The rover's address (`192.168.1.100:80`) and credentials (`AC13`/`AC13`) are hard-coded constants at the top of `AC13Comunicator`.

### Desktop Controller

[`Controller.java`](AC13%20Desktop%20Controller/src/main/java/com/rover/ac13/app/Controller.java) is a fixed-size Swing window. It has a 640×480 video panel, four arrow buttons, a CONNECT/DISCONNECT button and an "InfraRed Switch" checkbox.

- When the window opens, it creates an `AC13Comunicator` and starts an `image-receiver` virtual thread. That thread connects, then loops: it waits for each new frame with `awaitFrame(...)`, decodes it with `ImageIO`, and shows it on the Swing UI thread.
- Pressing an arrow sends the matching move (up/down = forward/backward, left/right = rotate). Releasing it calls `stopMovement()`.
- The controls are only enabled while connected. Closing the window disconnects from the rover.

Run it with:

```bash
./mvnw -pl 'AC13 Desktop Controller' -am install -DskipTests
./mvnw -pl 'AC13 Desktop Controller' exec:java
```

### Web Controller

A Spring Boot 4 web app (`spring-boot-starter-webmvc` and `actuator`) on port `8080`.

**Classes** (in `com.rover.ac13.web`)

- **`RoverService`**: a singleton that owns one `AC13Comunicator`. Its command methods are `synchronized`, so requests arriving at the same time don't send commands to the rover at the same time. `currentFrame()` returns the newest frame without waiting, or `null` when the rover isn't connected or streaming. `nextFrame(afterSequence, timeout)` waits for a newer frame; each `/api/stream` client uses it independently, so every client gets every frame exactly once. Its `@PreDestroy` hook closes the connection when the app shuts down.
- **`RoverController`**: the REST endpoints, listed below.
- **`DetectionController`** and **`DetectionService`**: receive object detections from an external recognizer and serve the latest report to the page (see [Image Recognition](#image-recognition)). Reports are checked before they're stored: size, labels, scores from 0 to 1, four-number boxes, and at most 100 detections.
- **`ApiTokenFilter`**: when an API token is configured, checks the `Authorization: Bearer <token>` header on mutating `/api/` requests (see [Authentication](#authentication)).
- **`CorrelationIdFilter`**: runs first. It reads the `X-Correlation-Id` header and reuses it only if it's 1–64 letters, digits, `.`, `_`, `:` or `-`. Otherwise, including when it's missing or contains line breaks, it makes a new UUID. That stops client input from splitting the response headers or adding fake lines to the logs. It stores the ID on the request and echoes it in the response. The Tomcat access log also prints it.
- **`ApiExceptionHandler`** and **`ApiError`**: for `RoverController` and `DetectionController` routes, turn exceptions into JSON. Bad input, such as an unknown move direction or a malformed request body, returns `400`. Anything else returns `500`. The JSON looks like `{timestamp, status, error, message, path, correlationId}`.

**REST API**

| Method | Path | Behavior |
|---|---|---|
| `GET` | `/api/status` | `{connected, streaming, infrared}` |
| `POST` | `/api/connect` | Connects to the rover. Returns `200` with the status, or `502 Bad Gateway` if the rover can't be reached. |
| `POST` | `/api/disconnect` | Disconnects and returns the status. |
| `POST` | `/api/infrared/toggle` | Turns infrared on or off and returns the status. |
| `POST` | `/api/move/{direction}` | `forward`, `backward`, `left` or `right`. Any other value returns `400`. |
| `POST` | `/api/stop` | Stops all movement. |
| `GET` | `/api/frame` | The latest frame as `image/jpeg`, or `503` if there is none. |
| `GET` | `/api/stream` | A continuous MJPEG stream (`multipart/x-mixed-replace; boundary=frame`). When no frames are coming in, it checks again every 100 ms and closes the stream after 5 seconds. Both times can be changed with `rover.stream.poll-interval-ms` and `rover.stream.idle-timeout-ms`. |
| `POST` | `/api/detections` | Stores a detection report: `{frame, width, height, detections: [{label, score, box: [x1, y1, x2, y2]}]}`. Returns `204`, or `400` if the report is invalid. |
| `GET` | `/api/detections` | The latest report plus `receivedAt` and `ageMs`, or `204` if none has been posted. |

**Browser UI** ([`static/index.html`](AC13%20Web%20Controller/src/main/resources/static/index.html))

- Asks for `/api/status` every 2 seconds. A coloured dot shows the state: grey for disconnected, yellow while busy, green for connected, red if the server can't be reached.
- Shows the live video from `/api/stream` once connected. If the stream ends or fails, the page tries again every 2 seconds.
- The move, stop and infrared buttons and the keyboard controls are disabled unless the rover is connected and no action is in progress.
- If connecting fails with `502`, the page says the rover couldn't be reached.
- If the server answers `401`, the page asks for the API token once and saves it in the browser's `localStorage`.
- While connected, it asks for `/api/detections` every 200 ms and draws the boxes and labels on a `<canvas>` over the video. The boxes are scaled the same way the video is fitted into its frame. Boxes from a report older than 1.5 seconds aren't drawn, so they disappear soon after the recognizer stops. The **Show detections** checkbox turns the overlay off (the choice is saved in the browser), and a line under the controls lists what was recognized.
- Movement works by holding a button (mouse or touch) or a key (arrow keys or WASD). While held, the page keeps sending the move command, faster the longer it's held: every 220 ms at first, then every 65 ms. Releasing the control, or leaving the window, sends `/api/stop`.
- Custom `404.html` and `500.html` pages in `public/error/` replace Spring's default error page.

**Authentication**

<a id="authentication"></a>Set the `ROVER_API_TOKEN` environment variable (or the `rover.api.token` property) to require a token on every `POST /api/*` request:

```bash
openssl rand -hex 32   # generate a token
ROVER_API_TOKEN=your-token-here ./scripts/run-web-controller.sh
```

Callers must send `Authorization: Bearer <token>`. Requests without it, or with the wrong token, get `401` with the usual JSON error. `GET` requests (`/api/status`, `/api/frame`, `/api/stream`), the page itself and `/actuator/*` stay public.

```bash
curl -X POST -H "Authorization: Bearer your-token-here" http://localhost:8080/api/connect
```

When no token is set, the check is off and the app logs a warning at startup. Don't put the real token in `application.properties`, because that file is committed. Without HTTPS the token travels in plain text, so only use this on a network you trust.

**Build metadata**

The `spring-boot-maven-plugin` `build-info` goal adds a `commit.hash` property to the build info. You can see it at `/actuator/info`. CI passes `-Dcommit.hash=${{ github.sha }}`. Local builds use `local` by default.

## Image Recognition

[`recognition/rover_detect.py`](recognition/rover_detect.py) is a Python script that detects objects in the rover's camera feed. It recognizes the 80 everyday object types of the COCO dataset: people, cups, chairs, bottles, cats, dogs and so on.

It reads the web controller's `/api/stream`, runs a pre-trained torchvision detector on each frame, and shows the video with labelled boxes in its own window. It also posts each frame's results to the web controller's `POST /api/detections`, so the **browser page draws the same boxes over its video**.

**How it works**

- A background thread reads the MJPEG stream and keeps only the newest frame. If detection is slower than the camera, frames are skipped instead of queuing up, so the boxes always match what the rover sees now. When the server closes the stream (for example, because the rover isn't connected), the thread reconnects every second.
- The default model is SSDLite with MobileNetV3, which takes about 110 ms per 640×480 frame on an Apple Silicon CPU. `--model fasterrcnn` is more accurate but noticeably slower. Both are BSD-licensed torchvision models trained on COCO.

**Setup** (Python 3.15 works; run from the repository root):

```bash
python3 -m venv recognition/.venv
recognition/.venv/bin/pip install -r recognition/requirements.txt
```

Your computer has to join the rover's Wi-Fi to drive it, and that usually means no internet. So download the model weights once beforehand, while you're online. They're cached in `~/.cache/torch`:

```bash
recognition/.venv/bin/python recognition/rover_detect.py --prepare
```

**Run** while the web controller is running and connected to the rover:

```bash
recognition/.venv/bin/python recognition/rover_detect.py
```

If the web controller has an API token set, posting detections needs it too. The script reads the same `ROVER_API_TOKEN` environment variable, or you can pass `--token`:

```bash
ROVER_API_TOKEN=your-token-here recognition/.venv/bin/python recognition/rover_detect.py
```

The script prints one line when posting starts working or fails, for example if the token is wrong. To show the boxes only in the browser, add `--no-window`. Press `q` or `Esc` in the window to quit. Useful options:

| Option | Effect |
|---|---|
| `--url http://host:8080` | Web controller address (default `http://localhost:8080`). |
| `--classes person,cup` | Only show these object types. `--list-classes` prints all names. |
| `--threshold 0.6` | Minimum confidence, 0–1 (default 0.5). |
| `--model fasterrcnn` | Use the slower, more accurate model. Run `--prepare` with it first. |
| `--json` | Print one JSON line per frame: `{"time", "frame", "width", "height", "detections": [{"label", "score", "box": [x1, y1, x2, y2]}]}`. |
| `--no-window` | Don't open a window. The browser overlay still works. |
| `--no-post` | Don't send detections to the web controller. |
| `--token TOKEN` | API token for posting (default: `$ROVER_API_TOKEN`). |
| `--device mps` | Run on another torch device. The default is `cpu` (or `cuda` if available). The Apple Silicon GPU (`mps`) works, but it isn't faster for the default model. It may help with `fasterrcnn`. |

**Tests** (stream parsing, frame handoff and posting; no PyTorch needed):

```bash
cd recognition && python3 -m unittest -v test_rover_detect
```

## Requirements

- JDK 25 (all modules compile with `--release 25`; Maven itself may run on JDK 25 or newer)
- Maven Wrapper, or Maven if you are working before the wrapper is generated
- To drive a real rover: your machine must be connected to the rover's Wi-Fi network so `192.168.1.100` is reachable. Without a rover, the web controller still starts. `/api/connect` returns `502`.

## Maven Toolchains (Coverage Uses JDK 25)

The `coverage` profile compiles and runs tests on JDK 25 through Maven Toolchains.

Create `~/.m2/toolchains.xml` with your local JDK 25 path:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
	<toolchain>
		<type>jdk</type>
		<provides>
			<version>25</version>
		</provides>
		<configuration>
			<jdkHome>/path/to/jdk-25</jdkHome>
		</configuration>
	</toolchain>
</toolchains>
```

On macOS, after installing JDK 25 you can discover the path with:

```bash
/usr/libexec/java_home -v 25
```

Coverage now uses that toolchain automatically:

```bash
./mvnw -Pcoverage clean verify
```

## Common Commands

Run the web controller locally from the workspace root, then open <http://localhost:8080>:

```bash
./scripts/run-web-controller.sh
```

Run tests for the web module:

```bash
./mvnw -pl 'AC13 Web Controller' test
```

Run the coverage build:

```bash
./mvnw -Pcoverage clean verify
```

Run the quality build:

```bash
./mvnw -Pquality clean verify -DskipTests
```

## Testing and Quality Gates

- **Tests**: The library tests are in `AC13ComunicatorTest`. The web module has unit tests for the service, controller and exception handler, plus the token filter (`ApiTokenFilterTest`) and the detection endpoints (`DetectionControllerTest`, `DetectionServiceTest`). Spring Boot integration tests cover the error pages and actuator endpoints (`ErrorPageIntegrationTest`), and the token check and a detection round trip with a token configured (`ApiTokenIntegrationTest`). The tests don't need real rover hardware: the communicator is passed into `RoverService` through a package-private constructor, so tests can swap it out. Surefire loads the Byte Buddy agent so Mockito can mock classes on JDK 25.
- **`coverage` profile**: Runs JaCoCo, and the build fails unless the `com.rover.ac13.web` package has **100% instruction and branch coverage**.
- **`quality` profile**: Runs Checkstyle with [`checkstyle.xml`](checkstyle.xml) on main and test sources. Any violation fails the build.

## Continuous Integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) ("Rover CI") runs on every push and pull request. It has two jobs, both on Temurin JDK 25:

- **Quality Gate**: `mvn -Pquality clean verify -DskipTests`
- **Coverage Gate**: `mvn -Pcoverage clean verify`. It uploads the JaCoCo HTML, XML and CSV reports as the `jacoco-reports` artifact, even if the build fails.

## Actuator

The web controller exposes:

- `GET /actuator/health`
- `GET /actuator/health/readiness`
- `GET /actuator/health/liveness`
- `GET /actuator/info`
- `GET /actuator/metrics`

## Notes

- The preferred local run command is the launcher script above.
- Build metadata is published through `/actuator/info` so you can quickly confirm the running version and commit hash.
- Set `ROVER_API_TOKEN` whenever the web controller is reachable by anyone else. See [Authentication](#authentication).
- See [`FUTURE_TODO.md`](FUTURE_TODO.md) for planned improvements.
