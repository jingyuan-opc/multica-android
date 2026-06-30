#!/bin/sh
# Gradle wrapper launcher (POSIX / macOS / Linux)
# Mirrors the script gradle generates via `gradle wrapper`.
# See https://docs.gradle.org/current/userguide/gradle_wrapper.html

set -e

DIR=$(cd "$(dirname "$0")" && pwd)
APP_HOME="$DIR"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "ERROR: gradle-wrapper.jar not found at $WRAPPER_JAR" >&2
    exit 1
fi

# Pick JAVA from JAVA_HOME, or search PATH
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_CMD="java"
else
    echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
    exit 1
fi

exec "$JAVA_CMD" \
    -Dorg.gradle.appname=gradlew \
    -classpath "$WRAPPER_JAR" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
