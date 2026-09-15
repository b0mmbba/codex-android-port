# Codex Android Port

Codex Android Port is an experimental Android APK wrapper around the Rust-based Codex CLI. It packages a native Codex binary with a simple phone UI so Codex can run directly on Android, work with local files, execute Android shell commands, and optionally use Android permissions such as all-files access, Accessibility, and root when the device provides them.

This project is intended for testing, automation experiments, Android file workflows, and research around running Codex-like local tooling on phones and emulators.

## Features

- Android APK shell for Codex CLI.
- Official Codex/ChatGPT device login flow.
- Chat-style interface on the phone.
- Model selector.
- Native Android shell execution through Codex.
- Access to the app workspace and `/sdcard` when Android grants all-files permission.
- Dark theme.
- Local chat history.
- Permission indicators for login, files, Accessibility, root, and ADB/system.
- Accessibility service scaffold for screen-control experiments.
- Root availability check through `su` when root exists.
- Separate APK builds for ARM64 phones and x86/x86_64 emulators such as LDPlayer.

## Current Status

The port is experimental. It can run Codex inside the Android app and execute shell/file tasks in the app workspace. Full phone control depends on Android security boundaries:

- All-files access is required for broad `/sdcard` file access.
- Accessibility is required for UI/screen control of other apps.
- Root is required for protected system areas when available.
- ADB/system access is controlled outside the app, usually from a connected PC or rooted/system environment.

## Local Development

### Requirements

- Windows host.
- Rust toolchain.
- Android SDK Platform Tools.
- Android SDK Build Tools.
- Android NDK.
- JDK 17.
- PowerShell.

The scripts expect Android tooling through these environment variables:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = "$env:ANDROID_HOME"
$env:ANDROID_NDK_HOME = "$env:ANDROID_HOME\ndk\<ndk-version>"
$env:JAVA_HOME = "$env:LOCALAPPDATA\Android\jdk-17"
```

### Build the Native Codex Binary

From the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\android\build-android.ps1 -Target aarch64-linux-android -Profile dev-small
```

Useful targets:

- `aarch64-linux-android` for `arm64-v8a` phones.
- `x86_64-linux-android` for 64-bit LDPlayer/emulators.
- `i686-linux-android` for older 32-bit x86 emulators.

### Build an APK

Example for ARM64 phones:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\android\build-apk.ps1 `
  -CodexBinary C:\tmp\codex-android-dev-target\aarch64-linux-android\dev-small\codex `
  -Abi arm64-v8a `
  -ApkName CodexAndroid-arm64-v8a.apk
```

Example for LDPlayer x86_64:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\android\build-apk.ps1 `
  -CodexBinary C:\tmp\codex-android-dev-target\x86_64-linux-android\dev-small\codex `
  -Abi x86_64 `
  -ApkName CodexAndroid-LDPlayer-x86_64.apk
```

## Install

Install the APK manually on a phone or emulator. On Android, allow installation from unknown sources if prompted.

After installation:

1. Open the app.
2. Tap `Login`.
3. Complete the official Codex/ChatGPT device login in the browser.
4. Return to the app.
5. Grant optional permissions from the access panel if needed.

## Environment Variables

The Android app normally uses device login and does not require a hard-coded API key.

For host-side builds, copy `.env.example` to `.env` only if you want local overrides. Do not commit `.env`.

Common optional variables:

- `ANDROID_HOME`
- `ANDROID_SDK_ROOT`
- `ANDROID_NDK_HOME`
- `JAVA_HOME`
- `CODEX_HOME`

## Deployment

This is a native Android APK project. Deployment means producing signed APK builds and distributing them manually or through an Android distribution channel.

For local/manual deployment:

1. Build the native Codex binary for the target CPU.
2. Build the APK for the matching ABI.
3. Install the APK on the phone or emulator.
4. Log in through the app.

For public release, replace the debug keystore with a real release keystore and keep signing keys private.

## Main Libraries and Tools

- Rust and Cargo for the Codex CLI core.
- Android SDK command-line tools: `aapt2`, `d8`, `zipalign`, `apksigner`.
- Android NDK for cross-compiling the native Codex binary.
- Java/JDK 17 for the Android activity and service layer.
- OpenAI Codex CLI crates from `codex-rs`.
- PowerShell build scripts in `android/`.

## Project Structure

```text
android/
  apk/                      Android manifest, Java UI, resources, Accessibility service
  mcp/                      Android device-control MCP bridge prototype
  build-android.ps1         Cross-compiles the Rust Codex binary for Android
  build-apk.ps1             Packages the Android APK
  install-device.ps1        Helper for adb-based installation/testing
  codex-android.config.toml Android Codex config template

codex-rs/                   Rust Codex workspace
codex-cli/                  Node launcher/package wrapper
sdk/                        SDK packages and examples
scripts/                    Repository tooling
docs/                       Upstream Codex documentation
```

## Security Notes

- Never commit real tokens, API keys, passwords, private keys, `.env` files, or private databases.
- The app uses the official device login flow and stores credentials in the app-private data directory.
- Root and Accessibility can give powerful control over the device. Enable them only on devices you control.

---

# Codex Android Port

Codex Android Port - экспериментальный Android APK-порт Rust-версии Codex CLI. Он упаковывает нативный бинарник Codex в Android-приложение с простым чат-интерфейсом, чтобы Codex мог запускаться прямо на телефоне, работать с файлами, выполнять Android shell-команды и использовать доступы Android, если они выданы.

Проект подходит для тестов, автоматизации Android, работы с файлами и экспериментов с Codex на телефонах и эмуляторах.

## Возможности

- APK-оболочка для Codex CLI.
- Вход через официальный Codex/ChatGPT device login.
- Чат-интерфейс прямо на телефоне.
- Выбор модели.
- Выполнение Android shell-команд через Codex.
- Работа с app workspace и `/sdcard`, если выдан доступ ко всем файлам.
- Темная тема.
- Локальная история сообщений.
- Индикаторы доступа: login, файлы, Accessibility, root, ADB/system.
- Заготовка Accessibility-сервиса для управления экраном.
- Проверка root через `su`, если root есть на устройстве.
- Отдельные APK для ARM64-телефонов и x86/x86_64 эмуляторов, включая LDPlayer.

## Текущий статус

Порт экспериментальный. Он уже может запускать Codex внутри Android-приложения и выполнять shell/file-задачи в рабочей папке приложения. Полное управление телефоном зависит от ограничений Android:

- Для широкого доступа к `/sdcard` нужен доступ ко всем файлам.
- Для управления чужими приложениями нужен Accessibility.
- Для закрытых системных областей нужен root, если он доступен.
- ADB/system-доступ обычно включается и управляется вне приложения: с ПК или из root/system-среды.

## Локальный запуск

### Требования

- Windows.
- Rust toolchain.
- Android SDK Platform Tools.
- Android SDK Build Tools.
- Android NDK.
- JDK 17.
- PowerShell.

Скрипты ожидают такие переменные окружения:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = "$env:ANDROID_HOME"
$env:ANDROID_NDK_HOME = "$env:ANDROID_HOME\ndk\<ndk-version>"
$env:JAVA_HOME = "$env:LOCALAPPDATA\Android\jdk-17"
```

### Сборка нативного Codex

Из корня репозитория:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\android\build-android.ps1 -Target aarch64-linux-android -Profile dev-small
```

Полезные цели:

- `aarch64-linux-android` для обычных `arm64-v8a` телефонов.
- `x86_64-linux-android` для 64-bit LDPlayer/эмуляторов.
- `i686-linux-android` для старых 32-bit x86 эмуляторов.

### Сборка APK

Пример для ARM64-телефонов:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\android\build-apk.ps1 `
  -CodexBinary C:\tmp\codex-android-dev-target\aarch64-linux-android\dev-small\codex `
  -Abi arm64-v8a `
  -ApkName CodexAndroid-arm64-v8a.apk
```

Пример для LDPlayer x86_64:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\android\build-apk.ps1 `
  -CodexBinary C:\tmp\codex-android-dev-target\x86_64-linux-android\dev-small\codex `
  -Abi x86_64 `
  -ApkName CodexAndroid-LDPlayer-x86_64.apk
```

## Установка

Установи APK вручную на телефон или эмулятор. Если Android спросит, разреши установку из неизвестных источников.

После установки:

1. Открой приложение.
2. Нажми `Login`.
3. Заверши официальный вход Codex/ChatGPT в браузере.
4. Вернись в приложение.
5. Выдай нужные разрешения в панели доступов.

## Переменные окружения

Android-приложение обычно использует device login и не требует вшитого API-ключа.

Для сборки на ПК можно скопировать `.env.example` в `.env`, если нужны локальные переопределения. `.env` нельзя коммитить.

Основные опциональные переменные:

- `ANDROID_HOME`
- `ANDROID_SDK_ROOT`
- `ANDROID_NDK_HOME`
- `JAVA_HOME`
- `CODEX_HOME`

## Деплой

Это Android APK-проект. Деплой означает сборку подписанного APK и его ручную публикацию или распространение через Android-канал.

Для локальной установки:

1. Собери нативный Codex под нужный процессор.
2. Собери APK под нужный ABI.
3. Установи APK на телефон или эмулятор.
4. Войди через кнопку `Login`.

Для публичного релиза замени debug-keystore на настоящий release-keystore и не публикуй ключи подписи.

## Основные библиотеки и инструменты

- Rust и Cargo для ядра Codex CLI.
- Android SDK tools: `aapt2`, `d8`, `zipalign`, `apksigner`.
- Android NDK для кросс-компиляции нативного бинарника.
- Java/JDK 17 для Android Activity и сервиса.
- Rust crates Codex из `codex-rs`.
- PowerShell-скрипты в `android/`.

## Структура проекта

```text
android/
  apk/                      Android manifest, Java UI, resources, Accessibility service
  mcp/                      прототип MCP-моста управления Android
  build-android.ps1         кросс-компиляция Rust Codex под Android
  build-apk.ps1             упаковка APK
  install-device.ps1        установка/тест через adb
  codex-android.config.toml шаблон Android-конфига Codex

codex-rs/                   Rust workspace Codex
codex-cli/                  Node launcher/package wrapper
sdk/                        SDK-пакеты и примеры
scripts/                    служебные скрипты репозитория
docs/                       документация upstream Codex
```

## Безопасность

- Не коммить реальные токены, API-ключи, пароли, приватные ключи, `.env` и приватные базы данных.
- Приложение использует официальный device login и хранит авторизацию в приватной папке Android-приложения.
- Root и Accessibility дают сильный контроль над устройством. Включай их только на своих устройствах.
