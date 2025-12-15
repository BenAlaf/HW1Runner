#!/usr/bin/env sh
# Minimal Gradle wrapper script (custom lightweight wrapper jar).
# Works for this project in Android Studio / terminal.

DIR="$(cd "$(dirname "$0")" && pwd)"
JAVA_CMD="${JAVA_HOME:-}/bin/java"
if [ ! -x "$JAVA_CMD" ]; then
  JAVA_CMD="java"
fi

exec "$JAVA_CMD" -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
