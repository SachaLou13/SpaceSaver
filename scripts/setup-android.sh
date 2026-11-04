#!/usr/bin/env bash
set -euo pipefail
sdk_dir="${ANDROID_HOME:-$HOME/android-sdk}"
mkdir -p "$sdk_dir/cmdline-tools"
if [ ! -x "$sdk_dir/cmdline-tools/latest/bin/sdkmanager" ]; then
  setup_dir="$(mktemp -d)"
  trap 'rm -rf "$setup_dir"' EXIT
  curl --fail --location --retry 3 https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o "$setup_dir/tools.zip"
  unzip -q "$setup_dir/tools.zip" -d "$setup_dir"
  mv "$setup_dir/cmdline-tools" "$sdk_dir/cmdline-tools/latest"
fi
export ANDROID_HOME="$sdk_dir"
export ANDROID_SDK_ROOT="$sdk_dir"
set +o pipefail
yes | "$sdk_dir/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null
set -o pipefail
"$sdk_dir/cmdline-tools/latest/bin/sdkmanager" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
printf 'sdk.dir=%s\n' "$sdk_dir" > local.properties
gradle wrapper --gradle-version 8.11.1
