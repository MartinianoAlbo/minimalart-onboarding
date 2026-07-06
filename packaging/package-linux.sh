#!/usr/bin/env bash
#
# Builds a portable, double-click Linux app-image of Arcor Onboarding, with a
# bundled Java 17 runtime (the end user does NOT need Java installed).
#
# Build prerequisites ON THIS MACHINE (only for whoever produces the package):
#   - JDK 17+ on PATH (provides `jpackage` and `jlink`)
#   - Maven (`mvn`) on PATH
# jpackage cannot cross-compile: run this script on Linux to get the Linux build.
#
set -euo pipefail

APP_NAME="ArcorOnboarding"
APP_VERSION="1.0.0"
MAIN_JAR="arcor-onboarding.jar"
MAIN_CLASS="co.minimalart.arcoronboarding.App"
# JDK modules the app needs (from `jdeps` + MySQL auth/charset safety net).
# If a run ever fails with a missing module, add it here (or drop --add-modules
# entirely to embed the full runtime).
MODULES="java.base,java.desktop,java.management,java.naming,java.security.sasl,java.sql,jdk.unsupported,jdk.crypto.ec,jdk.charsets"

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
STAGE="$ROOT/target/jpackage-input"
DIST="$ROOT/target/dist"

command -v mvn >/dev/null 2>&1 || { echo "ERROR: Maven (mvn) not on PATH. Install JDK 17 + Maven."; exit 1; }
command -v jpackage >/dev/null 2>&1 || { echo "ERROR: jpackage not on PATH. Use a JDK 17+."; exit 1; }

echo "==> Building fat JAR"
mvn -q clean package

echo "==> Staging jar (jpackage copies EVERYTHING in --input, so isolate the jar)"
rm -rf "$STAGE" "$DIST"
mkdir -p "$STAGE"
cp "target/$MAIN_JAR" "$STAGE/"

ICON_ARG=()
if [ -f "packaging/icons/icon.png" ]; then ICON_ARG=(--icon "packaging/icons/icon.png"); fi

echo "==> Running jpackage (Linux app-image)"
jpackage \
  --type app-image \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --vendor "Minimalart" \
  --input "$STAGE" \
  --main-jar "$MAIN_JAR" \
  --main-class "$MAIN_CLASS" \
  --java-options "-Dawt.useSystemAAFontSettings=on" \
  --add-modules "$MODULES" \
  --jlink-options "--strip-debug --no-header-files --no-man-pages" \
  "${ICON_ARG[@]}" \
  --dest "$DIST"

echo "==> Archiving (tar preserves the executable bit)"
( cd "$DIST" && tar -czf "$APP_NAME-linux.tar.gz" "$APP_NAME" )

echo ""
echo "DONE."
echo "  App-image : $DIST/$APP_NAME     (run: \"$DIST/$APP_NAME/bin/$APP_NAME\")"
echo "  Archive   : $DIST/$APP_NAME-linux.tar.gz"
echo ""
echo "To distribute: send the .tar.gz. The user extracts it and runs bin/$APP_NAME"
echo "(or create a .desktop launcher pointing at that binary for double-click)."
