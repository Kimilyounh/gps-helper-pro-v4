#!/usr/bin/env sh
set -e
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
GRADLE_VERSION=8.10.2
BASE_DIR="$HOME/.gradle-manual"
DIST="$BASE_DIR/gradle-$GRADLE_VERSION-bin.zip"
BIN="$BASE_DIR/gradle-$GRADLE_VERSION/bin/gradle"
mkdir -p "$BASE_DIR"
if [ ! -x "$BIN" ]; then
  if command -v curl >/dev/null 2>&1; then
    curl -L "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$DIST"
  elif command -v wget >/dev/null 2>&1; then
    wget "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$DIST"
  else
    echo "gradle/curl/wget not found" >&2; exit 1
  fi
  unzip -q "$DIST" -d "$BASE_DIR"
fi
exec "$BIN" "$@"
