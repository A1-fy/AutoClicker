#!/bin/sh

# Gradle wrapper - minimal robust version
# Determines JAVA_HOME and runs gradle-wrapper.jar

# Resolve APP_HOME
PRG="$0"
while [ -h "$PRG" ]; do
    ls=$(ls -ld -- "$PRG")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG="$(dirname "$PRG")/$link"
    fi
done
APP_HOME="$( cd "$( dirname "$PRG" )" > /dev/null && pwd )"

# Find Java
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVACMD" ] ; then
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
        exit 1
    fi
else
    JAVACMD="java"
    if ! command -v java >/dev/null 2>&1 ; then
        echo "ERROR: JAVA_HOME is not set and no 'java' command found in PATH" >&2
        exit 1
    fi
fi

# Run the Gradle wrapper
exec "$JAVACMD"     -Dorg.gradle.appname="gradlew"     -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar"     org.gradle.wrapper.GradleWrapperMain     "$@"
