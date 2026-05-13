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

echo "=== Building Volt Launcher for Linux ==="

# Step 1: Build Electron UI
echo "[1/5] Building Electron UI..."
cd ui
pnpm install
pnpm exec vite build
pnpm exec electron-builder --linux --dir
cd ..

# Step 2: Package electron binary as tar.gz
echo "[2/5] Packaging Electron binary..."
rm -f src/main/resources/electron-bin/*.tar.gz
if [ -d "src/main/resources/electron-bin/linux-unpacked" ]; then
    cd src/main/resources/electron-bin
    tar -czf linux-unpacked.tar.gz linux-unpacked
    rm -rf linux-unpacked
    cd ../../../..
else
    echo "ERROR: linux-unpacked directory not found!"
    exit 1
fi

# Remove other platform artifacts from electron-builder
rm -rf src/main/resources/electron-bin/mac src/main/resources/electron-bin/mac-arm64 src/main/resources/electron-bin/win-unpacked
rm -f src/main/resources/electron-bin/builder-debug.yml src/main/resources/electron-bin/builder-effective-config.yaml

# Step 3: Build GraalVM native image
echo "[3/5] Building GraalVM native image..."
mvn clean package -Pnative -DskipTests

if [ ! -f "target/$BINARY_NAME" ]; then
    echo "ERROR: Native image build failed - target/$BINARY_NAME not found!"
    exit 1
fi

echo "[3/5] Native image built: target/$BINARY_NAME ($(du -h "target/$BINARY_NAME" | cut -f1))"

# Step 4: Build AppImage
echo "[4/5] Building AppImage..."
APPDIR="target/appimage/${APP_NAME}.AppDir"
rm -rf target/appimage
mkdir -p "$APPDIR/usr/bin"
mkdir -p "$APPDIR/usr/share/icons/hicolor/256x256/apps"
mkdir -p "$APPDIR/usr/share/applications"

cp "target/$BINARY_NAME" "$APPDIR/usr/bin/$BINARY_NAME"
chmod +x "$APPDIR/usr/bin/$BINARY_NAME"

# Desktop entry
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

# Icon (generate a placeholder if none exists)
if [ -f "packaging/linux/voltlauncher.png" ]; then
    cp packaging/linux/voltlauncher.png "$APPDIR/voltlauncher.png"
    cp packaging/linux/voltlauncher.png "$APPDIR/usr/share/icons/hicolor/256x256/apps/voltlauncher.png"
else
    echo "WARNING: No icon found at packaging/linux/voltlauncher.png - AppImage will have no icon"
    # Create a minimal 1x1 PNG as placeholder
    printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde\x00\x00\x00\x0cIDATx\x9cc\xf8\x0f\x00\x00\x01\x01\x00\x05\x18\xd8N\x00\x00\x00\x00IEND\xaeB`\x82' > "$APPDIR/voltlauncher.png"
fi

# AppRun
cat > "$APPDIR/AppRun" <<'APPRUN'
#!/bin/bash
SELF=$(readlink -f "$0")
HERE=${SELF%/*}
exec "$HERE/usr/bin/volt-launcher" "$@"
APPRUN
chmod +x "$APPDIR/AppRun"

# Build AppImage (requires appimagetool in PATH)
if command -v appimagetool &> /dev/null; then
    ARCH=x86_64 appimagetool "$APPDIR" "target/${APP_NAME}-${VERSION}-x86_64.AppImage"
    echo "AppImage created: target/${APP_NAME}-${VERSION}-x86_64.AppImage"
else
    echo "WARNING: appimagetool not found in PATH. Skipping AppImage creation."
    echo "  Install from: https://github.com/AppImage/appimagetool/releases"
fi

# Step 5: Build .deb package
echo "[5/5] Building .deb package..."
DEB_DIR="target/deb/${BINARY_NAME}_${VERSION}"
rm -rf target/deb
mkdir -p "$DEB_DIR/DEBIAN"
mkdir -p "$DEB_DIR/usr/bin"
mkdir -p "$DEB_DIR/usr/share/applications"
mkdir -p "$DEB_DIR/usr/share/icons/hicolor/256x256/apps"

cp "target/$BINARY_NAME" "$DEB_DIR/usr/bin/$BINARY_NAME"
chmod +x "$DEB_DIR/usr/bin/$BINARY_NAME"

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
echo "  Native binary: target/$BINARY_NAME"
[ -f "target/${APP_NAME}-${VERSION}-x86_64.AppImage" ] && echo "  AppImage:      target/${APP_NAME}-${VERSION}-x86_64.AppImage"
echo "  Deb package:   target/${BINARY_NAME}_${VERSION}_amd64.deb"
