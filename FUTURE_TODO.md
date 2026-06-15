# Future To-Do

This is the prioritized backlog for the rover project.

## Done
- Added a `README.md` with the exact run/test/coverage/quality commands.
- Added Maven Wrapper so contributors do not need a local Maven install.
- Added `/actuator/info` build metadata such as version and commit hash.
- Added smoke tests for `/actuator/info`, `/actuator/health/readiness`, and `/actuator/health/liveness`.
- Improved the local run path for the web controller and documented the preferred command.

## Later
- Add token-based authentication for mutating web endpoints.
- Keep `/api/status`, `/actuator/health`, and other read-only endpoints public.
- Consider HTTPS or a trusted-network deployment so tokens are not sent in cleartext.
- Add stricter validation for API inputs and future query params.
- Add a single place for API error responses if more routes are added.
- Consider rate limiting on movement/stop endpoints.
- Split `AC13Comunicator` into smaller pieces: protocol building, transport, and high-level orchestration.
- Add injectable seams for sockets, HTTP, and threading to make hardware-free testing easier.
- Improve connection state feedback in the web UI.
- Add clearer stream failure/reconnect behavior.
- Make the control surface more explicit about when commands are disabled.

## Keep
- Keep the `quality` profile as a Checkstyle gate.
- Keep the `coverage` profile as a JaCoCo gate.
- Upload JaCoCo reports as CI artifacts.
- Add a fast web-module-only test job if CI feedback time becomes an issue.
