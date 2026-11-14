#!/usr/bin/env bash
# Ambiente de DESARROLLO: corre la app localmente en http://localhost:8090
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -B -ntp install -DskipTests
./mvnw -B -ntp -pl web spring-boot:run
