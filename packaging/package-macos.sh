#!/usr/bin/env bash
#
# Builds a portable, double-click macOS app (ArcorOnboarding.app) with a bundled
# Java 17 runtime (the end user does NOT need Java installed).
#
# Build prerequisites ON THIS MACHINE (only for whoever produces the package):
#   - JDK 17+ on PATH (provides `jpackage` and `jlink`) — e.g. Temurin 17
#   - Maven (`mvn`) on PATH
# jpackage cannot cross-compile: run this script ON macOS to get the macOS build.
#
# Gatekeeper note: this build is UNSIGNED. The first time, the user must
# right-click the .app -> Open (or run: xattr -dr com.apple.quarantine ArcorOnboarding.app).
# To ship a signed/notarized app, add --mac-sign and related options (needs an
# Apple Developer account).
#
set -euo pipefail

APP_NAME="ArcorOnboarding"
APP_VERSION="1.0.0"
MAIN_JAR="arcor-onboarding.jar"
MAIN_CLASS="co.minimalart.arcoronboarding.App"
MODULES="java.base,java.desktop,java.management,java.naming,java.security.sasl,java.sql,jdk.unsupported,jdk.crypto.ec,jdk.charsets"

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
STAGE="$ROOT/target/jpackage-input"
DIST="$ROOT/target/dist"

command -v mvn >/dev/null 2>&1 || { echo "ERROR: Maven (mvn) not on PATH. Install JDK 17 + Maven."; exit 1; }
command -v jpackage >/dev/null 2>&1 || { echo "ERROR: jpackage not on PATH. Use a JDK 17+."; exit 1; }

echo "==> Building fat JAR"
mvn -q clean package

echo "==> Staging jar"
rm -rf "$STAGE" "$DIST"
mkdir -p "$STAGE"
cp "target/$MAIN_JAR" "$STAGE/"

ICON_ARG=()
if [ -f "packaging/icons/icon.icns" ]; then
  ICON_ARG=(--icon "packaging/icons/icon.icns")
else
  echo "NOTE: packaging/icons/icon.icns not found — using the default jpackage icon."
  echo "      To brand it, create icon.icns from packaging/icons/icon.png (macOS):"
  echo "        mkdir icon.iconset && sips -z 512 512 packaging/icons/icon.png --out icon.iconset/icon_512x512.png && iconutil -c icns -o packaging/icons/icon.icns icon.iconset"
fi

echo "==> Running jpackage (macOS .app image)"
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

echo "==> Zipping the .app (ditto preserves the bundle correctly)"
( cd "$DIST" && ditto -c -k --keepParent "$APP_NAME.app" "$APP_NAME-macos.zip" )

echo ""
echo "DONE."
echo "  App    : $DIST/$APP_NAME.app     (double-click to run)"
echo "  Archive: $DIST/$APP_NAME-macos.zip"
echo ""
echo "Optional installer: re-run jpackage with --type dmg (uses built-in hdiutil, no extra tools)."
