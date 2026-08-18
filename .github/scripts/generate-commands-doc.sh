#!/usr/bin/env bash
# Generates docs/source/commands.md from litoria CLI help output.
# Note: the canonical docs are now in asciidoc (docs/source/commands.adoc).
# This script generates a markdown snapshot for reference.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
JAR="$PROJECT_DIR/target/litoria-quarkus-1.0.0-SNAPSHOT-runner.jar"
OUT="$PROJECT_DIR/docs/source/commands.md"

if [ ! -f "$JAR" ]; then
  echo "Uber-jar not found. Building..."
  (cd "$PROJECT_DIR" && ./mvnw package -DskipTests -q)
fi

java -Dquarkus.log.level=OFF -Dquarkus.banner.enabled=false \
  -jar "$JAR" --help=md > "$OUT"

echo "Generated $OUT"
