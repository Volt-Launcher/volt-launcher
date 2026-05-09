#!/bin/bash
set -e

# Generates GraalVM native-image configuration files by running the application
# with the native-image tracing agent. Exercise all app features (login, launch,
# browse instances, etc.) then close the app to write the config files.
#
# Prerequisites:
# - GraalVM JDK 21+ installed and on PATH
# - Electron UI already built (run build-linux.sh/build-macos.sh first, or
#   at minimum build the electron binary and tar.gz)
#
# Usage:
#   ./scripts/generate-native-config.sh          # fresh generation
#   ./scripts/generate-native-config.sh --merge   # merge with existing configs

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

# Use GraalVM JDK if GRAALVM_HOME is set
if [ -n "$GRAALVM_HOME" ]; then
    export JAVA_HOME="$GRAALVM_HOME"
    export PATH="$GRAALVM_HOME/bin:$PATH"
    echo "Using GraalVM at: $GRAALVM_HOME"
    echo "  java: $(java -version 2>&1 | head -1)"
fi

CONFIG_DIR="src/main/resources/META-INF/native-image/app.voltlauncher/volt-launcher"

# Build the shaded JAR (without native image)
echo "[1/3] Building shaded JAR..."
mvn clean package -DskipTests

SHADED_JAR=$(ls target/volt-launcher-*-shaded.jar 2>/dev/null | head -1)
if [ -z "$SHADED_JAR" ]; then
    SHADED_JAR="target/volt-launcher-0.1.0.jar"
fi

if [ ! -f "$SHADED_JAR" ]; then
    echo "ERROR: Shaded JAR not found at $SHADED_JAR"
    exit 1
fi

# Determine agent mode
AGENT_MODE="config-output-dir"
if [ "$1" = "--merge" ] && [ -d "$CONFIG_DIR" ]; then
    AGENT_MODE="config-merge-dir"
    echo "[2/3] Running with tracing agent (MERGE mode)..."
else
    echo "[2/3] Running with tracing agent (FRESH mode)..."
fi

echo ""
echo "  The application will start now."
echo "  Exercise ALL features: login, create instance, launch, browse, settings, etc."
echo "  Close the application when done to write the config files."
echo ""

java \
    "-agentlib:native-image-agent=${AGENT_MODE}=${CONFIG_DIR}" \
    -jar "$SHADED_JAR"

echo ""
echo "[3/3] Config files written to: $CONFIG_DIR"
echo ""
ls -la "$CONFIG_DIR"
echo ""
echo "Review the generated files, then rebuild with: mvn clean package -Pnative"
