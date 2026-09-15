# Codex on Android

This folder contains the first Android port layer for Codex CLI.

The intended MVP is:

1. Cross-compile the `codex` Rust binary for `aarch64-linux-android`.
2. Push the binary to a device.
3. Run Codex from Termux or `adb shell`.
4. Expose phone-control tools through the local MCP server in `mcp/android_device_mcp.py`.

This keeps the Android work separate from upstream Codex internals while the core Rust crates are
made Android-clean.

## Host setup

Install these on the Windows host:

- Rust with the `aarch64-linux-android` target.
- Android SDK platform tools.
- Android NDK.

The scripts expect either `ANDROID_NDK_HOME` or `NDK_HOME` to point at the NDK root.

## Build

From the repository root:

```powershell
.\android\build-android.ps1
```

The output binary is expected at:

```text
C:\tmp\codex-android-target\aarch64-linux-android\release\codex
```

The target directory intentionally defaults to `C:\tmp\codex-android-target` on Windows because
some Windows App Control policies block Cargo build scripts when they are emitted under the
workspace `target\` directory.

## Install to a connected device

Enable USB debugging, connect the phone, then run:

```powershell
.\android\install-device.ps1
```

This pushes:

- `codex` to `/data/local/tmp/codex`
- the MCP bridge to `/data/local/tmp/android_device_mcp.py`
- a sample config to `/sdcard/Download/codex-android.config.toml`

For a Termux-based setup, copy the config into `~/.codex/config.toml` or merge its
`[mcp_servers.android_device]` section into the existing config.

## Build the APK shell

The APK shell is the first Android app layer for the port. It includes:

- a launcher activity with status/actions;
- the MCP bridge and sample config as exportable assets;
- an accessibility service declaration for future phone-control integration.

Build it from the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\android\build-apk.ps1
```

The signed debug APK is written to:

```text
C:\tmp\codex-android-apk\codex-android-debug.apk
```

At this stage the APK does not bundle the Rust Codex CLI binary. The CLI cross-build currently
reaches `openssl-sys` and needs the next dependency-porting step before a native binary can be
packaged into the app.

## Phone control tools

The bridge provides intentionally small, explicit tools:

- `screen_state`: current focused window and screen-related dumpsys output.
- `dump_ui`: XML from `uiautomator dump`.
- `screenshot`: saves a PNG on the device.
- `tap`: taps screen coordinates.
- `swipe`: swipes between coordinates.
- `keyevent`: sends Android key events such as `HOME`, `BACK`, or `ENTER`.
- `type_text`: types text into the focused field.
- `start_activity`: runs `am start`.
- `list_packages`: lists installed packages.

Most control goes through Android's built-in `input`, `uiautomator`, `screencap`, `am`, and `pm`
commands, so it works over `adb shell` and inside Termux when those commands are available.

## Current blockers

The Windows host now has Rust, Android SDK platform-tools, Android SDK Build Tools, Android NDK,
JDK 17, Visual Studio C++ Build Tools, and `just` installed.

The current Rust CLI cross-build blocker is:

- fix or remove the `openssl-sys` dependency path for `aarch64-linux-android`.

The likely next Rust fixes after that are:

- decide whether Android should select `SandboxType::None` or a future Android-specific sandbox;
- verify PTY behavior under Android/Termux;
- keep Windows sandbox code fully behind Windows target gates for Android builds;
- disable desktop-only app launching and updater paths on Android.
