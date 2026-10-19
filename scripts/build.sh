#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
 JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export JAVA_HOME
fi
if [ -z "${ANDROID_HOME:-}" ]; then
 if [ -n "${ANDROID_SDK_ROOT:-}" ]; then ANDROID_HOME="$ANDROID_SDK_ROOT"; export ANDROID_HOME
 elif [ -d "$HOME/Library/Android/sdk" ]; then ANDROID_HOME="$HOME/Library/Android/sdk"; export ANDROID_HOME
 fi
fi
exec ./gradlew --project-cache-dir "$HOME/.cache/currency-converter/gradle-project" --console=plain "$@"
