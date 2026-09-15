param(
    [string]$Target = "aarch64-linux-android",
    [int]$ApiLevel = 24,
    [string]$Profile = "dev-small",
    [string]$TargetDir = ""
)

$ErrorActionPreference = "Stop"

function Require-Command($Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing required command: $Name"
    }
}

Require-Command "cargo"
Require-Command "rustup"

$ndkRoot = $env:ANDROID_NDK_HOME
if (-not $ndkRoot) {
    $ndkRoot = $env:NDK_HOME
}
if (-not $ndkRoot) {
    throw "Set ANDROID_NDK_HOME or NDK_HOME to your Android NDK root."
}
if (-not (Test-Path $ndkRoot)) {
    throw "Android NDK root does not exist: $ndkRoot"
}

$hostTag = "windows-x86_64"
$toolchainBin = Join-Path $ndkRoot "toolchains\llvm\prebuilt\$hostTag\bin"

switch ($Target) {
    "aarch64-linux-android" {
        $clangTarget = "aarch64-linux-android"
        $linkerEnv = "CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER"
        $ccEnv = "CC_aarch64_linux_android"
        $cxxEnv = "CXX_aarch64_linux_android"
        $arEnv = "AR_aarch64_linux_android"
    }
    "armv7-linux-androideabi" {
        $clangTarget = "armv7a-linux-androideabi"
        $linkerEnv = "CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER"
        $ccEnv = "CC_armv7_linux_androideabi"
        $cxxEnv = "CXX_armv7_linux_androideabi"
        $arEnv = "AR_armv7_linux_androideabi"
    }
    "x86_64-linux-android" {
        $clangTarget = "x86_64-linux-android"
        $linkerEnv = "CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER"
        $ccEnv = "CC_x86_64_linux_android"
        $cxxEnv = "CXX_x86_64_linux_android"
        $arEnv = "AR_x86_64_linux_android"
    }
    "i686-linux-android" {
        $clangTarget = "i686-linux-android"
        $linkerEnv = "CARGO_TARGET_I686_LINUX_ANDROID_LINKER"
        $ccEnv = "CC_i686_linux_android"
        $cxxEnv = "CXX_i686_linux_android"
        $arEnv = "AR_i686_linux_android"
    }
    default {
        throw "Unsupported Android Rust target: $Target"
    }
}

$clang = Join-Path $toolchainBin "$clangTarget$ApiLevel-clang.cmd"
$clangxx = Join-Path $toolchainBin "$clangTarget$ApiLevel-clang++.cmd"
$ar = Join-Path $toolchainBin "llvm-ar.exe"

if (-not (Test-Path $clang)) {
    throw "Android clang wrapper was not found: $clang"
}
if (-not (Test-Path $clangxx)) {
    throw "Android clang++ wrapper was not found: $clangxx"
}
if (-not (Test-Path $ar)) {
    throw "Android llvm-ar was not found: $ar"
}

rustup target add $Target

if (-not $TargetDir) {
    $TargetDir = $env:CARGO_TARGET_DIR
}
if (-not $TargetDir) {
    $TargetDir = "C:\tmp\codex-android-target"
}
New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null

[Environment]::SetEnvironmentVariable($linkerEnv, $clang, "Process")
[Environment]::SetEnvironmentVariable($ccEnv, $clang, "Process")
[Environment]::SetEnvironmentVariable($cxxEnv, $clangxx, "Process")
[Environment]::SetEnvironmentVariable($arEnv, $ar, "Process")
$env:PATH = "$toolchainBin;$env:PATH"

$manifest = Join-Path $PSScriptRoot "..\codex-rs\Cargo.toml"
$args = @(
    "build",
    "--manifest-path", $manifest,
    "-p", "codex-cli",
    "--target", $Target,
    "--target-dir", $TargetDir
)

if ($Profile -eq "release") {
    $args += "--release"
} elseif ($Profile -ne "dev") {
    $args += "--profile", $Profile
}

cargo @args
if ($LASTEXITCODE -ne 0) {
    throw "cargo build failed with exit code $LASTEXITCODE"
}

$binary = Join-Path $TargetDir "$Target\$Profile\codex"
Write-Host "Built Android Codex binary:"
Write-Host (Resolve-Path $binary)
