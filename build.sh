#!/bin/bash
set -e

TARGET=$1
if [ -z "$TARGET" ]; then
    if [[ "$OSTYPE" == "darwin"* ]]; then
        TARGET="--mac"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        TARGET="--linux"
    else
        TARGET="--win"
    fi
fi

cd ui
pnpm install
pnpm exec vite build
pnpm exec electron-builder $TARGET --dir
cd ..

rm -f src/main/resources/electron-bin/*.tar.gz

for os_dir in mac mac-arm64 linux-unpacked win-unpacked; do
    if [ -d "src/main/resources/electron-bin/$os_dir" ]; then
        echo "Taring $os_dir..."
        cd "src/main/resources/electron-bin"
        tar -czf "$os_dir.tar.gz" "$os_dir"
        rm -rf "$os_dir"
        cd ../../../..
    fi
done

mvn clean package
