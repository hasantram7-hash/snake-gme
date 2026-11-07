#!/bin/bash
# Gradle wrapper script - run 'gradlew' from project root to build
# This requires a gradle-wrapper.jar in gradle/wrapper/
# If missing, download from https://repo.gradle.org/gradle/distributions/gradle-8.5-bin.zip
# and extract the gradle/wrapper/gradle-wrapper.jar into this project's gradle/wrapper/
# Then: chmod +x gradlew && ./gradlew assembleRelease

if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "ERROR: gradle/wrapper/gradle-wrapper.jar not found"
    echo "Download from: https://repo.gradle.org/gradle/distributions/gradle-8.5-bin.zip"
    echo "Extract and place at: gradle/wrapper/gradle-wrapper.jar"
    exit 1
fi

exec java -jar gradle/wrapper/gradle-wrapper.jar "$@"