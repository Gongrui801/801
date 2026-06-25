#!/bin/bash
set -e

export JAVA_HOME=/root/.local/share/mise/installs/java/17.0.2
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export BUILD_TOOLS=$ANDROID_HOME/build-tools/33.0.2
export PLATFORM=$ANDROID_HOME/platforms/android-33
export PATH=$BUILD_TOOLS:$ANDROID_HOME/platform-tools:$PATH

PROJECT_DIR=/workspace/android-app
BUILD_DIR=$PROJECT_DIR/build
GEN_DIR=$BUILD_DIR/gen
OBJ_DIR=$BUILD_DIR/obj
DEX_DIR=$BUILD_DIR/dex

echo "=== Cleaning ==="
rm -rf $BUILD_DIR
mkdir -p $GEN_DIR $OBJ_DIR $DEX_DIR

echo "=== Generating R.java ==="
aapt2 compile -o $BUILD_DIR/res.zip \
    --dir $PROJECT_DIR/app/src/main/res \
    2>&1

aapt2 link -o $BUILD_DIR/unaligned.apk \
    --manifest $PROJECT_DIR/app/src/main/AndroidManifest.xml \
    -I $PLATFORM/android.jar \
    --java $GEN_DIR \
    $BUILD_DIR/res.zip \
    --auto-add-overlay \
    2>&1

echo "=== Compiling Java sources ==="
find $PROJECT_DIR/app/src/main/java -name "*.java" > $BUILD_DIR/sources.txt
find $GEN_DIR -name "*.java" >> $BUILD_DIR/sources.txt

javac -source 1.8 -target 1.8 \
    -bootclasspath $PLATFORM/android.jar \
    -cp $PLATFORM/android.jar \
    -d $OBJ_DIR \
    @$BUILD_DIR/sources.txt \
    2>&1

echo "=== Converting to DEX ==="
$BUILD_TOOLS/d8 --lib $PLATFORM/android.jar \
    --output $DEX_DIR \
    $(find $OBJ_DIR -name "*.class") \
    2>&1

echo "=== Adding DEX to APK ==="
cp $BUILD_DIR/unaligned.apk $BUILD_DIR/unaligned_with_dex.apk
cd $DEX_DIR
zip -u $BUILD_DIR/unaligned_with_dex.apk classes.dex 2>&1 || true
cd -

echo "=== Aligning APK ==="
zipalign -f 4 $BUILD_DIR/unaligned_with_dex.apk $BUILD_DIR/aligned.apk 2>&1

echo "=== Generating keystore ==="
if [ ! -f $BUILD_DIR/debug.keystore ]; then
    keytool -genkey -v -keystore $BUILD_DIR/debug.keystore \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=Debug, OU=Debug, O=Debug, L=Debug, ST=Debug, C=CN" 2>&1
fi

echo "=== Signing APK ==="
apksigner sign --ks $BUILD_DIR/debug.keystore \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out $BUILD_DIR/CLSMorningAlarm-debug.apk \
    $BUILD_DIR/aligned.apk 2>&1

echo "=== Build complete ==="
ls -lh $BUILD_DIR/CLSMorningAlarm-debug.apk
