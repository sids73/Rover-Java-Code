#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

./mvnw -Pcoverage -pl 'AC13 Web Controller' -am package -DskipTests -Dcommit.hash=local
exec java -jar 'AC13 Web Controller/target/ac13-web-controller-1.0.0-SNAPSHOT.jar'