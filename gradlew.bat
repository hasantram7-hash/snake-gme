@echo off
REM Gradle wrapper script for Windows
REM This requires a gradle-wrapper.jar in gradle/wrapper/
REM Then: gradlew.bat assembleRelease

if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo ERROR: gradle\wrapper\gradle-wrapper.jar not found
    exit /b 1
)

java -classpath "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*