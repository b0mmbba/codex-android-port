param(
    [string]$Target = "aarch64-linux-android",
    [string]$Profile = "dev-small",
    [string]$DeviceDir = "/data/local/tmp",
    [string]$TargetDir = ""
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "Missing required command: adb"
}

if (-not $TargetDir) {
    $TargetDir = $env:CARGO_TARGET_DIR
}
if (-not $TargetDir) {
    $TargetDir = "C:\tmp\codex-android-dev-target"
}

$binary = Join-Path $TargetDir "$Target\$Profile\codex"
$bridge = Join-Path $PSScriptRoot "mcp\android_device_mcp.py"
$config = Join-Path $PSScriptRoot "codex-android.config.toml"

if (-not (Test-Path $binary)) {
    throw "Codex binary was not found. Run .\android\build-android.ps1 first."
}

adb push $binary "$DeviceDir/codex"
adb push $bridge "$DeviceDir/android_device_mcp.py"
adb push $config "/sdcard/Download/codex-android.config.toml"
adb shell chmod 755 "$DeviceDir/codex"
adb shell chmod 755 "$DeviceDir/android_device_mcp.py"

Write-Host "Installed Codex Android files."
Write-Host "Binary: $DeviceDir/codex"
Write-Host "MCP bridge: $DeviceDir/android_device_mcp.py"
Write-Host "Sample config: /sdcard/Download/codex-android.config.toml"
Write-Host ""
Write-Host "Quick check:"
Write-Host "  adb shell $DeviceDir/codex --version"
