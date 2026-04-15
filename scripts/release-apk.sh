#!/usr/bin/env sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_root"

dist_dir="$repo_root/dist"
apk_dir="$repo_root/app/build/outputs/apk/debug"
compile_sdk=$(sed -n 's/^[[:space:]]*compileSdk = \([0-9][0-9]*\)$/\1/p' "$repo_root/app/build.gradle.kts")
build_tools_version="${ANDROID_BUILD_TOOLS_VERSION:-35.0.0}"

if [ -z "$compile_sdk" ]; then
  echo "Could not read compileSdk from app/build.gradle.kts" >&2
  exit 1
fi

find_sdkmanager() {
  if command -v sdkmanager >/dev/null 2>&1; then
    command -v sdkmanager
    return 0
  fi

  for candidate in \
    "${ANDROID_SDK_ROOT:-}/cmdline-tools/latest/bin/sdkmanager" \
    "${ANDROID_HOME:-}/cmdline-tools/latest/bin/sdkmanager" \
    "/usr/lib/android-sdk/cmdline-tools/latest/bin/sdkmanager" \
    "/usr/lib/android-sdk/cmdline-tools/bin/sdkmanager" \
    "/usr/lib/android-sdk/tools/bin/sdkmanager"
  do
    if [ -n "$candidate" ] && [ -x "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  return 1
}

prepare_android_sdk() {
  sdkmanager_cmd=$(find_sdkmanager || true)
  if [ -z "${sdkmanager_cmd:-}" ]; then
    echo "sdkmanager not found. Install Android command-line tools first." >&2
    echo "Then run: sdkmanager --licenses" >&2
    echo "And install: platforms;android-$compile_sdk build-tools;$build_tools_version" >&2
    exit 1
  fi

  sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/lib/android-sdk}}"
  echo "Preparing Android SDK with: $sdkmanager_cmd"
  yes | "$sdkmanager_cmd" --sdk_root="$sdk_root" --licenses >/dev/null
  "$sdkmanager_cmd" --sdk_root="$sdk_root" \
    "platforms;android-$compile_sdk" \
    "build-tools;$build_tools_version"
}

prepare_android_sdk

gradle_cmd=
if [ -x "$repo_root/gradlew" ]; then
  gradle_cmd="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  gradle_cmd="gradle"
else
  echo "No Gradle runner found. Add ./gradlew or install gradle." >&2
  exit 1
fi

echo "Building debug APK with: $gradle_cmd"
"$gradle_cmd" assembleDebug

if [ ! -d "$apk_dir" ]; then
  echo "Debug APK output directory not found: $apk_dir" >&2
  exit 1
fi

mkdir -p "$dist_dir"
find "$dist_dir" -maxdepth 1 -type f -name '*.apk' -delete

find "$apk_dir" -maxdepth 1 -type f -name '*.apk' -exec cp {} "$dist_dir/" \;

if ! find "$dist_dir" -maxdepth 1 -type f -name '*.apk' | grep -q .; then
  echo "No APK files were copied to $dist_dir" >&2
  exit 1
fi

echo "Debug APK ready:"
find "$dist_dir" -maxdepth 1 -type f -name '*.apk' -print
