#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

VERSION="0.1.0"
APP_NAME="VoltLauncher"
BINARY_NAME="volt-launcher"
OUTPUT_DIR="build/macos"

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
echo "[1/3] Building Electron UI..."
cd ui
pnpm install
pnpm exec vite build
pnpm exec electron-builder --mac --dir
cd ..

# Clean up builder metadata and other platform artifacts
rm -f src/main/resources/electron-bin/builder-debug.yml \
      src/main/resources/electron-bin/builder-effective-config.yaml
rm -rf src/main/resources/electron-bin/linux-unpacked \
       src/main/resources/electron-bin/win-unpacked 2>/dev/null || true

if [ ! -d "src/main/resources/electron-bin/$ELECTRON_DIR" ]; then
    echo "ERROR: $ELECTRON_DIR not found in src/main/resources/electron-bin!"
    exit 1
fi

# Step 2: Build GraalVM native image
echo "[2/3] Building GraalVM native image..."
mvn clean package -Pnative -DskipTests

if [ ! -f "target/$BINARY_NAME" ]; then
    echo "ERROR: Native image build failed - target/$BINARY_NAME not found!"
    exit 1
fi
echo "[2/3] Native image built: target/$BINARY_NAME ($(du -h "target/$BINARY_NAME" | cut -f1))"

# Step 3: Assemble build output
echo "[3/3] Assembling build output in $OUTPUT_DIR..."
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/electron"

# Copy native binary
cp "target/$BINARY_NAME" "$OUTPUT_DIR/$BINARY_NAME"
chmod +x "$OUTPUT_DIR/$BINARY_NAME"

# Copy electron binary alongside main binary
cp -r "src/main/resources/electron-bin/$ELECTRON_DIR" "$OUTPUT_DIR/electron/"

# Clean electron-bin after copying
rm -rf src/main/resources/electron-bin/mac \
       src/main/resources/electron-bin/mac-arm64 2>/dev/null || true

# Step 4: Build .dmg
echo "[4/4] Building .dmg..."
APP_BUNDLE="$OUTPUT_DIR/${APP_NAME}.app"
mkdir -p "$APP_BUNDLE/Contents/MacOS/electron"
mkdir -p "$APP_BUNDLE/Contents/Resources"

cp "$OUTPUT_DIR/$BINARY_NAME" "$APP_BUNDLE/Contents/MacOS/$APP_NAME"
chmod +x "$APP_BUNDLE/Contents/MacOS/$APP_NAME"

# Bundle electron inside the .app (sibling to the binary)
cp -r "$OUTPUT_DIR/electron/$ELECTRON_DIR" "$APP_BUNDLE/Contents/MacOS/electron/"

cat > "$APP_BUNDLE/Contents/Info.plist" <<PLIST
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

if [ -f "packaging/macos/AppIcon.icns" ]; then
    cp packaging/macos/AppIcon.icns "$APP_BUNDLE/Contents/Resources/AppIcon.icns"
fi

DMG_STAGING="target/dmg-staging"
rm -rf "$DMG_STAGING"
mkdir -p "$DMG_STAGING"
cp -r "$APP_BUNDLE" "$DMG_STAGING/"
ln -s /Applications "$DMG_STAGING/Applications"

DMG_OUTPUT="target/${APP_NAME}-${VERSION}-${DMG_ARCH}.dmg"
hdiutil create -volname "$APP_NAME" -srcfolder "$DMG_STAGING" -ov -format UDZO "$DMG_OUTPUT"

echo ""
echo "=== Build complete ==="
echo "  Output directory: $OUTPUT_DIR/"
echo "  Native binary:    $OUTPUT_DIR/$BINARY_NAME"
echo "  Electron:         $OUTPUT_DIR/electron/$ELECTRON_DIR"
echo "  DMG:              $DMG_OUTPUT"
