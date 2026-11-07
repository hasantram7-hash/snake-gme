#!/bin/bash
# Gradle wrapper script - run 'gradlew' from project root to build
# This requires a gradle-wrapper.jar in gradle/wrapper/
# Then: chmod +x gradlew && ./gradlew assembleRelease

if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "ERROR: gradle/wrapper/gradle-wrapper.jar not found"
    exit 1
fi

exec java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain "$@"