# Future To-Do

This is the prioritized backlog for the rover project.

## Done
- Added a `README.md` with the exact run/test/coverage/quality commands.
- Added Maven Wrapper so contributors do not need a local Maven install.
- Added `/actuator/info` build metadata such as version and commit hash.
- Added smoke tests for `/actuator/info`, `/actuator/health/readiness`, and `/actuator/health/liveness`.
- Improved the local run path for the web controller and documented the preferred command.
- Added shared-token authentication (`rover.api.token` / `ROVER_API_TOKEN`) for mutating `/api/**` requests; read-only endpoints and actuator stay public.
- Fixed `/api/stream` busy-looping while no frames are available; idle streams now back off and close, and the UI reconnects.
- Improved connection state feedback in the web UI (status indicator, connecting state, rover-unreachable message).
- Controls in the web UI are disabled while disconnected or while an action is in progress.

## Later
- Consider HTTPS or a trusted-network deployment so tokens are not sent in cleartext.
- Add stricter validation for API inputs and future query params.
- Add a single place for API error responses if more routes are added.
- Consider rate limiting on movement/stop endpoints.
- Split `AC13Comunicator` into smaller pieces: protocol building, transport, and high-level orchestration.
- Add injectable seams for sockets, HTTP, and threading to make hardware-free testing easier.
- Add server-side rover reconnect when the video socket drops mid-session.

## Keep
- Keep the `quality` profile as a Checkstyle gate.
- Keep the `coverage` profile as a JaCoCo gate.
- Upload JaCoCo reports as CI artifacts.
- Add a fast web-module-only test job if CI feedback time becomes an issue.
