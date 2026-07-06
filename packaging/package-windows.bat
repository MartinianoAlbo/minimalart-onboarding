@echo off
REM ==========================================================================
REM  Builds a portable, double-click Windows app-image of Arcor Onboarding,
REM  with a bundled Java 17 runtime (the end user does NOT need Java installed).
REM
REM  Build prerequisites ON THIS MACHINE (only for whoever produces the package):
REM    - JDK 17+ on PATH (provides jpackage and jlink) - e.g. Temurin 17
REM    - Maven (mvn) on PATH
REM  jpackage cannot cross-compile: run this ON Windows to get the Windows build.
REM
REM  This produces a portable app-image (a folder with ArcorOnboarding.exe) that
REM  needs NO installer and NO admin rights. For a real installer (.msi/.exe),
REM  install the WiX Toolset and change --type app-image to --type msi.
REM ==========================================================================
setlocal enabledelayedexpansion

set APP_NAME=ArcorOnboarding
set APP_VERSION=1.0.0
set MAIN_JAR=arcor-onboarding.jar
set MAIN_CLASS=co.minimalart.arcoronboarding.App
set MODULES=java.base,java.desktop,java.management,java.naming,java.security.sasl,java.sql,jdk.unsupported,jdk.crypto.ec,jdk.charsets

REM Move to the project root (this script lives in packaging\)
cd /d "%~dp0.."

where mvn >nul 2>nul || (echo ERROR: Maven ^(mvn^) not on PATH. Install JDK 17 + Maven. & exit /b 1)
where jpackage >nul 2>nul || (echo ERROR: jpackage not on PATH. Use a JDK 17+. & exit /b 1)

echo ==^> Building fat JAR
call mvn -q clean package || exit /b 1

echo ==^> Staging jar
if exist target\jpackage-input rmdir /s /q target\jpackage-input
if exist target\dist rmdir /s /q target\dist
mkdir target\jpackage-input
copy /y "target\%MAIN_JAR%" "target\jpackage-input\" >nul

set ICON=
if exist packaging\icons\icon.ico set ICON=--icon packaging\icons\icon.ico

echo ==^> Running jpackage (Windows app-image)
jpackage ^
  --type app-image ^
  --name %APP_NAME% ^
  --app-version %APP_VERSION% ^
  --vendor "Minimalart" ^
  --input target\jpackage-input ^
  --main-jar %MAIN_JAR% ^
  --main-class %MAIN_CLASS% ^
  --java-options "-Dawt.useSystemAAFontSettings=on" ^
  --add-modules %MODULES% ^
  --jlink-options "--strip-debug --no-header-files --no-man-pages" ^
  %ICON% ^
  --dest target\dist || exit /b 1

echo ==^> Zipping
powershell -NoProfile -Command "Compress-Archive -Path 'target/dist/%APP_NAME%' -DestinationPath 'target/dist/%APP_NAME%-windows.zip' -Force"

echo.
echo DONE.
echo   App-image: target\dist\%APP_NAME%\%APP_NAME%.exe   (double-click to run)
echo   Archive  : target\dist\%APP_NAME%-windows.zip
echo.
echo To distribute: send the .zip. The user extracts it and double-clicks %APP_NAME%.exe
endlocal
