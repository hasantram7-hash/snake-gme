@echo off
REM Gradle wrapper script for Windows
REM This requires a gradle-wrapper.jar in gradle/wrapper/
REM If missing, download from https://repo.gradle.org/gradle/distributions/gradle-8.5-bin.zip
REM and extract the gradle/wrapper/gradle-wrapper.jar into this project's gradle/wrapper/
REM Then: gradlew.bat assembleRelease

if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo ERROR: gradle\wrapper\gradle-wrapper.jar not found
    echo Download from: https://repo.gradle.org/gradle/distributions/gradle-8.5-bin.zip
    echo Extract and place at: gradle\wrapper\gradle-wrapper.jar
    exit /b 1
)

java -jar gradle\wrapper\gradle-wrapper.jar %*