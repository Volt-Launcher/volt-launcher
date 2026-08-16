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
echo "[1/4] Building Electron UI..."
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

# Step 2: Build GraalVM native image
echo "[2/4] Building GraalVM native image..."
mvn clean package -Pnative -DskipTests

if [ ! -f "volt-app/target/$BINARY_NAME" ]; then
    echo "ERROR: Native image build failed - volt-app/target/$BINARY_NAME not found!"
    exit 1
fi
echo "[2/4] Native image built: volt-app/target/$BINARY_NAME ($(du -h "volt-app/target/$BINARY_NAME" | cut -f1))"

# Step 3: Assemble build output
echo "[3/4] Assembling build output in $OUTPUT_DIR..."
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/electron"

# Copy native binary
cp "volt-app/target/$BINARY_NAME" "$OUTPUT_DIR/$BINARY_NAME"
chmod +x "$OUTPUT_DIR/$BINARY_NAME"

# Copy electron binary alongside main binary
cp -r "volt-app/src/main/resources/electron-bin/$ELECTRON_DIR" "$OUTPUT_DIR/electron/"

# Ship the installer next to the launcher so `java -jar volt-setup.jar` works from the
# extracted archive with no arguments.
if [ -f "volt-setup/target/volt-setup.jar" ]; then
    cp "volt-setup/target/volt-setup.jar" "$OUTPUT_DIR/volt-setup.jar"
fi
if [ -f "packaging/linux/voltlauncher.png" ]; then
    cp "packaging/linux/voltlauncher.png" "$OUTPUT_DIR/voltlauncher.png"
fi

# Clean electron-bin after copying
rm -rf volt-app/src/main/resources/electron-bin/linux-unpacked 2>/dev/null || true

# Step 4: Build AppImage and .deb
echo "[4/4] Building AppImage and .deb..."

# --- AppImage ---
APPDIR="target/appimage/${APP_NAME}.AppDir"
rm -rf target/appimage
mkdir -p "$APPDIR/usr/bin"
mkdir -p "$APPDIR/usr/share/icons/hicolor/256x256/apps"
mkdir -p "$APPDIR/usr/share/applications"

cp "$OUTPUT_DIR/$BINARY_NAME" "$APPDIR/usr/bin/$BINARY_NAME"
chmod +x "$APPDIR/usr/bin/$BINARY_NAME"

# Bundle electron inside AppDir
cp -r "$OUTPUT_DIR/electron" "$APPDIR/usr/bin/electron"

cat > "$APPDIR/voltlauncher.desktop" <<'DESKTOP'
[Desktop Entry]
Name=VoltLauncher
Exec=volt-launcher
Icon=voltlauncher
Type=Application
Categories=Game;
Comment=A modern Minecraft launcher
DESKTOP

cp "$APPDIR/voltlauncher.desktop" "$APPDIR/usr/share/applications/voltlauncher.desktop"

if [ -f "packaging/linux/voltlauncher.png" ]; then
    cp packaging/linux/voltlauncher.png "$APPDIR/voltlauncher.png"
    cp packaging/linux/voltlauncher.png "$APPDIR/usr/share/icons/hicolor/256x256/apps/voltlauncher.png"
else
    echo "WARNING: No icon found at packaging/linux/voltlauncher.png"
    printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde\x00\x00\x00\x0cIDATx\x9cc\xf8\x0f\x00\x00\x01\x01\x00\x05\x18\xd8N\x00\x00\x00\x00IEND\xaeB`\x82' > "$APPDIR/voltlauncher.png"
fi

cat > "$APPDIR/AppRun" <<'APPRUN'
#!/bin/bash
SELF=$(readlink -f "$0")
HERE=${SELF%/*}
exec "$HERE/usr/bin/volt-launcher" "$@"
APPRUN
chmod +x "$APPDIR/AppRun"

if command -v appimagetool &> /dev/null; then
    ARCH=x86_64 appimagetool "$APPDIR" "target/${APP_NAME}-${VERSION}-x86_64.AppImage"
    echo "AppImage created: target/${APP_NAME}-${VERSION}-x86_64.AppImage"
else
    echo "WARNING: appimagetool not found in PATH. Skipping AppImage creation."
    echo "  Install from: https://github.com/AppImage/appimagetool/releases"
fi

# --- .deb package ---
DEB_DIR="target/deb/${BINARY_NAME}_${VERSION}"
rm -rf target/deb
mkdir -p "$DEB_DIR/DEBIAN"
mkdir -p "$DEB_DIR/usr/bin"
mkdir -p "$DEB_DIR/usr/share/applications"
mkdir -p "$DEB_DIR/usr/share/icons/hicolor/256x256/apps"

cp "$OUTPUT_DIR/$BINARY_NAME" "$DEB_DIR/usr/bin/$BINARY_NAME"
chmod +x "$DEB_DIR/usr/bin/$BINARY_NAME"

# Bundle electron in deb package
cp -r "$OUTPUT_DIR/electron" "$DEB_DIR/usr/lib/voltlauncher-electron"

cat > "$DEB_DIR/DEBIAN/control" <<CONTROL
Package: volt-launcher
Version: ${VERSION}
Section: games
Priority: optional
Architecture: amd64
Maintainer: Volt Launcher <contact@voltlauncher.app>
Description: A modern Minecraft launcher
 VoltLauncher is a modern, fast Minecraft launcher built with
 GraalVM native image and Electron UI.
CONTROL

cat > "$DEB_DIR/usr/share/applications/voltlauncher.desktop" <<'DESKTOP'
[Desktop Entry]
Name=VoltLauncher
Exec=volt-launcher
Icon=voltlauncher
Type=Application
Categories=Game;
Comment=A modern Minecraft launcher
DESKTOP

if [ -f "packaging/linux/voltlauncher.png" ]; then
    cp packaging/linux/voltlauncher.png "$DEB_DIR/usr/share/icons/hicolor/256x256/apps/voltlauncher.png"
fi

dpkg-deb --build "$DEB_DIR" "target/${BINARY_NAME}_${VERSION}_amd64.deb"
echo ".deb created: target/${BINARY_NAME}_${VERSION}_amd64.deb"

echo ""
echo "=== Build complete ==="
echo "  Output directory: $OUTPUT_DIR/"
echo "  Native binary:    $OUTPUT_DIR/$BINARY_NAME"
echo "  Electron:         $OUTPUT_DIR/electron/$ELECTRON_DIR"
[ -f "target/${APP_NAME}-${VERSION}-x86_64.AppImage" ] && echo "  AppImage:         target/${APP_NAME}-${VERSION}-x86_64.AppImage"
echo "  Deb package:      target/${BINARY_NAME}_${VERSION}_amd64.deb"
