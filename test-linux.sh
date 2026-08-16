#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

VERSION="0.2.0"
APP_NAME="VoltLauncher"
BINARY_NAME="volt-launcher"
OUTPUT_DIR="build/linux"
ELECTRON_DIR="linux-unpacked"

# Use GraalVM JDK if GRAALVM_HOME is set
if [ -n "$GRAALVM_HOME" ]; then
    export JAVA_HOME="$GRAALVM_HOME"
    export PATH="$GRAALVM_HOME/bin:$PATH"
    echo "Using GraalVM at: $GRAALVM_HOME"
    echo "  java: $(java -version 2>&1 | head -1)"
fi

echo "=== Building Volt Launcher for Linux ==="

# Step 1: Build Electron UI
echo "[1/2] Building Electron UI..."
cd ui
pnpm install
pnpm exec vite build
pnpm exec electron-builder --linux --dir
cd ..

# Clean up builder metadata and other platform artifacts
rm -f volt-app/src/main/resources/electron-bin/builder-debug.yml \
      volt-app/src/main/resources/electron-bin/builder-effective-config.yaml
rm -rf volt-app/src/main/resources/electron-bin/mac \
       volt-app/src/main/resources/electron-bin/mac-arm64 \
       volt-app/src/main/resources/electron-bin/win-unpacked 2>/dev/null || true

if [ ! -d "volt-app/src/main/resources/electron-bin/$ELECTRON_DIR" ]; then
    echo "ERROR: $ELECTRON_DIR not found in volt-app/src/main/resources/electron-bin!"
    exit 1
fi

echo "[2/2] Starting java backend..."
mvn clean compile exec:exec
