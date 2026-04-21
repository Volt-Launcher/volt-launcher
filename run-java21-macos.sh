#!/bin/sh
set -eu
JAVA_HOME="$(/usr/libexec/java_home -v 21)"
JCEF_ROOT="$HOME/.thelauncherproject/jcef/jcef_app.app/Contents"
JCEF_FRAMEWORKS="$JCEF_ROOT/Frameworks"
JCEF_JAVA="$JCEF_ROOT/Java"

export DYLD_FRAMEWORK_PATH="$JCEF_FRAMEWORKS${DYLD_FRAMEWORK_PATH:+:$DYLD_FRAMEWORK_PATH}"
export DYLD_FALLBACK_FRAMEWORK_PATH="$JCEF_FRAMEWORKS${DYLD_FALLBACK_FRAMEWORK_PATH:+:$DYLD_FALLBACK_FRAMEWORK_PATH}"

exec "$JAVA_HOME/bin/java" "-Djava.library.path=$JCEF_JAVA" "$@"


