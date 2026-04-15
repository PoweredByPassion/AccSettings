#!/usr/bin/env sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

activity_file="$repo_root/app/src/main/kotlin/crazyboyfeng/accSettings/SettingsActivity.kt"
gradle_file="$repo_root/app/build.gradle.kts"

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

echo "PASS: review regression checks"
