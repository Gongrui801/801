#!/bin/bash
export JAVA_HOME=/root/.local/share/mise/installs/java/17.0.2
export PATH=$JAVA_HOME/bin:/opt/gradle/gradle-7.6.4/bin:$PATH
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/33.0.2:$PATH

cd /workspace/android-app

echo "Java version:"
java -version 2>&1
echo "Gradle version:"
gradle --version 2>&1 | head -5
echo "Starting build..."

gradle assembleDebug --no-daemon 2>&1
