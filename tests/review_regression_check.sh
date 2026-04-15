#!/usr/bin/env sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

activity_file="$repo_root/app/src/main/kotlin/crazyboyfeng/accSettings/SettingsActivity.kt"
gradle_file="$repo_root/app/build.gradle.kts"
root_gradle_file="$repo_root/build.gradle.kts"
release_script="$repo_root/scripts/release-apk.sh"
workflow_file="$repo_root/.github/workflows/build-apk.yml"

if grep -q "setOnApplyWindowInsetsListener" "$activity_file"; then
  echo "FAIL: SettingsActivity still installs the manual window inset listener."
  exit 1
fi

compile_sdk=$(sed -n 's/^[[:space:]]*compileSdk = \([0-9][0-9]*\)$/\1/p' "$gradle_file")
target_sdk=$(sed -n 's/^[[:space:]]*targetSdk = \([0-9][0-9]*\)$/\1/p' "$gradle_file")

if [ -z "$compile_sdk" ] || [ -z "$target_sdk" ]; then
  echo "FAIL: Could not read compileSdk/targetSdk from app/build.gradle.kts."
  exit 1
fi

if [ "$target_sdk" -ne "$compile_sdk" ]; then
  echo "FAIL: targetSdk ($target_sdk) does not match compileSdk ($compile_sdk)."
  exit 1
fi

if grep -q "org.jetbrains.kotlin.android\").version(\"1.6.21" "$root_gradle_file" \
  || grep -q "org.jetbrains.kotlin.android\") version \"1.6.21" "$root_gradle_file"; then
  echo "FAIL: Kotlin Gradle plugin is still pinned to 1.6.21."
  exit 1
fi

if [ ! -x "$release_script" ]; then
  echo "FAIL: scripts/release-apk.sh is missing or not executable."
  exit 1
fi

if ! grep -q "sdkmanager" "$release_script" || ! grep -q -- "--licenses" "$release_script"; then
  echo "FAIL: release script does not prepare Android SDK licenses/packages."
  exit 1
fi

if [ ! -f "$workflow_file" ]; then
  echo "FAIL: .github/workflows/build-apk.yml is missing."
  exit 1
fi

if ! grep -q "workflow_dispatch:" "$workflow_file"; then
  echo "FAIL: build workflow does not support manual runs."
  exit 1
fi

if ! grep -q "tags:" "$workflow_file" || ! grep -q "v\\*" "$workflow_file"; then
  echo "FAIL: build workflow does not trigger on v* tags."
  exit 1
fi

if ! grep -q "android-actions/setup-android" "$workflow_file" || ! grep -q "sdkmanager" "$workflow_file"; then
  echo "FAIL: build workflow does not prepare Android SDK dependencies."
  exit 1
fi

if ! grep -q "gh release create" "$workflow_file" && ! grep -q "softprops/action-gh-release" "$workflow_file"; then
  echo "FAIL: build workflow does not publish releases for tag runs."
  exit 1
fi

echo "PASS: review regression checks"
