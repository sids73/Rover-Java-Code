# Rover Java Code

Modernized rover controller workspace with a shared communication library, a desktop controller, and a Spring Boot web controller.

## Requirements

- JDK 25 (all modules compile with `--release 25`; Maven itself may run on JDK 25 or newer)
- Maven Wrapper, or Maven if you are working before the wrapper is generated

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

Run the web controller locally from the workspace root:

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

## Actuator

The web controller exposes:

- `GET /actuator/health`
- `GET /actuator/health/readiness`
- `GET /actuator/health/liveness`
- `GET /actuator/info`

## Notes

- The preferred local run command is the launcher script above.
- Build metadata is published through `/actuator/info` so you can quickly confirm the running version and commit hash.