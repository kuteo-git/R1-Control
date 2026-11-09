# Toolchain for this project, and nowhere else. Same toolchain as mytube-app.
#
#   source env.sh
#
# Everything lives on the external volume because the internal disk is nearly
# full. When /Volumes/Data2 is not mounted this project cannot be built.

if [ ! -d /Volumes/Data2 ]; then
  echo "Data2 is not mounted — nothing here will build." >&2
  return 1 2>/dev/null || exit 1
fi

DEV=/Volumes/Data2/dev

export JAVA_HOME="$DEV/jdk/Contents/Home"
export ANDROID_SDK_ROOT="$DEV/android-sdk"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export GRADLE_USER_HOME="$DEV/gradle"
# Kotlin/Native's toolchain and prebuilt klibs; defaults to ~/.konan otherwise.
export KONAN_DATA_DIR="$DEV/konan"
export ANDROID_AVD_HOME="$DEV/avd"
export ANDROID_EMULATOR_HOME="$DEV/emulator-home"

export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

echo "java   $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
echo "sdk    $ANDROID_SDK_ROOT"
echo "gradle $GRADLE_USER_HOME"
echo "konan  $KONAN_DATA_DIR"
