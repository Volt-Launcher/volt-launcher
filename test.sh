#!/bin/bash
set -e

# Auto-detect platform and delegate to the appropriate build script
if [[ "$OSTYPE" == "darwin"* ]]; then
    exec "$(dirname "$0")/test-macos.sh" "$@"
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    exec "$(dirname "$0")/test-linux.sh" "$@"
else
    echo "Use test-windows.bat on Windows"
    exit 1
fi
