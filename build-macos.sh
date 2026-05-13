#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

VERSION="0.1.0"
APP_NAME="VoltLauncher"
BINARY_NAME="volt-launcher"

# Use GraalVM JDK if GRAALVM_HOME is set
if [ -n "$GRAALVM_HOME" ]; then
    export JAVA_HOME="$GRAALVM_HOME"
    export PATH="$GRAALVM_HOME/bin:$PATH"
    echo "Using GraalVM at: $GRAALVM_HOME"
    echo "  java: $(java -version 2>&1 | head -1)"
fi

echo "=== Building Volt Launcher for macOS ==="

# Detect architecture
ARCH=$(uname -m)
if [ "$ARCH" = "arm64" ]; then
    ELECTRON_DIR="mac-arm64"
    DMG_ARCH="arm64"
else
    ELECTRON_DIR="mac"
    DMG_ARCH="x64"
fi
echo "Detected architecture: $ARCH (electron dir: $ELECTRON_DIR)"

# Step 1: Build Electron UI
echo "[1/4] Building Electron UI..."
cd ui
pnpm install
pnpm exec vite build
pnpm exec electron-builder --mac --dir
cd ..

# Step 2: Package electron binary as tar.gz
echo "[2/4] Packaging Electron binary..."
rm -f src/main/resources/electron-bin/*.tar.gz

if [ -d "src/main/resources/electron-bin/$ELECTRON_DIR" ]; then
    cd src/main/resources/electron-bin
    tar -czf "$ELECTRON_DIR.tar.gz" "$ELECTRON_DIR"
    rm -rf "$ELECTRON_DIR"
    cd ../../../..
else
    echo "ERROR: $ELECTRON_DIR directory not found!"
    exit 1
fi

# Remove other platform artifacts
rm -rf src/main/resources/electron-bin/mac src/main/resources/electron-bin/mac-arm64 src/main/resources/electron-bin/linux-unpacked src/main/resources/electron-bin/win-unpacked 2>/dev/null || true
rm -f src/main/resources/electron-bin/builder-debug.yml src/main/resources/electron-bin/builder-effective-config.yaml

# Step 3: Build GraalVM native image
echo "[3/4] Building GraalVM native image..."
mvn clean package -Pnative -DskipTests

if [ ! -f "target/$BINARY_NAME" ]; then
    echo "ERROR: Native image build failed - target/$BINARY_NAME not found!"
    exit 1
fi

echo "[3/4] Native image built: target/$BINARY_NAME ($(du -h "target/$BINARY_NAME" | cut -f1))"

# Step 4: Build .dmg
echo "[4/4] Building .dmg..."
DMG_STAGING="target/dmg-staging"
rm -rf "$DMG_STAGING"
mkdir -p "$DMG_STAGING/${APP_NAME}.app/Contents/MacOS"
mkdir -p "$DMG_STAGING/${APP_NAME}.app/Contents/Resources"

# Copy native binary into .app bundle
cp "target/$BINARY_NAME" "$DMG_STAGING/${APP_NAME}.app/Contents/MacOS/${APP_NAME}"
chmod +x "$DMG_STAGING/${APP_NAME}.app/Contents/MacOS/${APP_NAME}"

# Create Info.plist
cat > "$DMG_STAGING/${APP_NAME}.app/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>${APP_NAME}</string>
    <key>CFBundleIdentifier</key>
    <string>app.voltlauncher</string>
    <key>CFBundleName</key>
    <string>${APP_NAME}</string>
    <key>CFBundleVersion</key>
    <string>${VERSION}</string>
    <key>CFBundleShortVersionString</key>
    <string>${VERSION}</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon</string>
    <key>LSMinimumSystemVersion</key>
    <string>11.0</string>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
PLIST

# Copy icon if available
if [ -f "packaging/macos/AppIcon.icns" ]; then
    cp packaging/macos/AppIcon.icns "$DMG_STAGING/${APP_NAME}.app/Contents/Resources/AppIcon.icns"
fi

# Create symlink to Applications folder for drag-and-drop install
ln -s /Applications "$DMG_STAGING/Applications"

# Build DMG
DMG_OUTPUT="target/${APP_NAME}-${VERSION}-${DMG_ARCH}.dmg"
hdiutil create -volname "$APP_NAME" -srcfolder "$DMG_STAGING" -ov -format UDZO "$DMG_OUTPUT"

echo ""
echo "=== Build complete ==="
echo "  Native binary: target/$BINARY_NAME"
echo "  DMG:           $DMG_OUTPUT"
