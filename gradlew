#!/usr/bin/env sh

set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME%/}

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ ! -f "$CLASSPATH" ]; then
  echo "ERROR: gradle-wrapper.jar is missing. Please generate the Gradle wrapper with:"
  echo "  gradle wrapper"
  echo "or download it from the official Gradle distribution and place it at:"
  echo "  $CLASSPATH"
  exit 1
fi

JAVA_OPTS=${JAVA_OPTS:-"-Xmx64m -Xms256m"}
export JAVA_OPTS

exec "$JAVA_HOME/bin/java" ${JAVA_OPTS} -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
