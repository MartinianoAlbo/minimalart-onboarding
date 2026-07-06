# Packaging — native double-click apps per OS

These scripts turn the fat JAR into a **portable, double-click desktop app with a
bundled Java 17 runtime**, so the end user (a new dev) does **not** need Java installed.

## Why one script per OS

`jpackage` (the JDK tool that bundles the runtime + native launcher) **cannot
cross-compile**. The bundled runtime, the launcher binary format (ELF / PE / Mach-O),
and the packaging tools are all OS-specific. So each OS's app must be built **on that
OS**. Run the matching script on each machine:

| OS | Script | Output (under `target/dist/`) | How the user runs it |
|----|--------|-------------------------------|----------------------|
| Linux | `package-linux.sh` | `ArcorOnboarding/` + `ArcorOnboarding-linux.tar.gz` | `bin/ArcorOnboarding` |
| macOS | `package-macos.sh` | `ArcorOnboarding.app` + `ArcorOnboarding-macos.zip` | double-click the `.app` |
| Windows | `package-windows.bat` | `ArcorOnboarding\` + `ArcorOnboarding-windows.zip` | double-click `ArcorOnboarding.exe` |

## Build prerequisites (only for whoever produces a package — NOT the end user)

- **JDK 17+** on `PATH` (provides `jpackage` + `jlink`) — e.g. Eclipse Temurin 17.
- **Maven** (`mvn`) on `PATH`.

The end user needs none of this — the runtime is embedded in the app.

## Usage

```bash
# Linux / macOS
./packaging/package-linux.sh
./packaging/package-macos.sh
```
```bat
REM Windows (from the project root)
packaging\package-windows.bat
```

Each script: builds the fat JAR (`mvn clean package`) → runs `jpackage` with an
embedded, trimmed runtime → produces the app-image and a compressed archive.

## Icons

- `icons/icon.png` (Linux) and `icons/icon.ico` (Windows) are included.
- `icons/icon.icns` (macOS) is **not** included (it must be generated on a Mac). The
  macOS script prints the one-liner to create it; without it, the default icon is used.

## Notes

- **Runtime size:** the embedded runtime is trimmed to the modules the app actually
  uses (`--add-modules` in the scripts). If a run ever fails with a missing module,
  add it to the `MODULES` list (or remove `--add-modules` to embed the full runtime).
- **macOS Gatekeeper:** the build is unsigned, so the first launch needs right-click →
  Open (or `xattr -dr com.apple.quarantine ArcorOnboarding.app`). For a signed/notarized
  app, add `--mac-sign …` (requires an Apple Developer account).
- **Installers instead of portable apps:** change `--type app-image` to `--type deb`
  (Linux, needs `dpkg`), `--type dmg` (macOS, built-in), or `--type msi` (Windows, needs
  the WiX Toolset).
