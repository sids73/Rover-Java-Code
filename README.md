# Rover Java Code

Modernized rover controller workspace with a shared communication library, a desktop controller, and a Spring Boot web controller.

## Requirements

- Java 26 for normal development builds
- Maven Wrapper, or Maven if you are working before the wrapper is generated

## Maven Toolchains (Coverage Uses JDK 22)

The `coverage` profile is configured to use JDK 22 through Maven Toolchains.

Create `~/.m2/toolchains.xml` with your local JDK 22 path:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
	<toolchain>
		<type>jdk</type>
		<provides>
			<version>22</version>
		</provides>
		<configuration>
			<jdkHome>/path/to/jdk-22</jdkHome>
		</configuration>
	</toolchain>
</toolchains>
```

On macOS, after installing JDK 22 you can discover the path with:

```bash
/usr/libexec/java_home -v 22
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