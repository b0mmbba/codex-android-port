param(
    [string]$BuildToolsVersion = "35.0.1",
    [string]$Platform = "android-35",
    [string]$OutDir = "C:\tmp\codex-android-apk",
    [string]$CodexBinary = "C:\tmp\codex-android-dev-target\aarch64-linux-android\dev-small\codex",
    [ValidateSet("arm64-v8a", "armeabi-v7a", "x86_64", "x86")]
    [string]$Abi = "arm64-v8a",
    [string]$ApkName = "codex-android-debug.apk",
    [string]$Keystore = "C:\tmp\codex-android-debug.keystore"
)

$ErrorActionPreference = "Stop"

$AndroidSdk = $env:ANDROID_HOME
if (-not $AndroidSdk) {
    $AndroidSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
$JdkRoot = $env:JAVA_HOME
if (-not $JdkRoot) {
    $JdkRoot = Join-Path $env:LOCALAPPDATA "Android\jdk-17"
}

$BuildTools = Join-Path $AndroidSdk "build-tools\$BuildToolsVersion"
$AndroidJar = Join-Path $AndroidSdk "platforms\$Platform\android.jar"
$Aapt2 = Join-Path $BuildTools "aapt2.exe"
$D8 = Join-Path $BuildTools "d8.bat"
$Zipalign = Join-Path $BuildTools "zipalign.exe"
$ApkSigner = Join-Path $BuildTools "apksigner.bat"
$Javac = Join-Path $JdkRoot "bin\javac.exe"
$Jar = Join-Path $JdkRoot "bin\jar.exe"
$Keytool = Join-Path $JdkRoot "bin\keytool.exe"

foreach ($Path in @($AndroidJar, $Aapt2, $D8, $Zipalign, $ApkSigner, $Javac, $Jar, $Keytool)) {
    if (-not (Test-Path $Path)) {
        throw "Missing build tool: $Path"
    }
}

$ProjectDir = Join-Path $PSScriptRoot "apk"
$Manifest = Join-Path $ProjectDir "AndroidManifest.xml"
$ResDir = Join-Path $ProjectDir "res"
$SrcDir = Join-Path $ProjectDir "src\main\java"
$AssetsDir = Join-Path $OutDir "assets"
$NativeLibDir = Join-Path $OutDir "native-lib\lib\$Abi"
$GenDir = Join-Path $OutDir "gen"
$ClassesDir = Join-Path $OutDir "classes"
$DexDir = Join-Path $OutDir "dex"
$CompiledRes = Join-Path $OutDir "compiled-res.zip"
$UnsignedApk = Join-Path $OutDir "codex-android-unsigned.apk"
$AlignedApk = Join-Path $OutDir "codex-android-aligned.apk"
$SignedApk = Join-Path $OutDir $ApkName

Remove-Item -Recurse -Force $OutDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $AssetsDir, $NativeLibDir, $GenDir, $ClassesDir, $DexDir | Out-Null
Copy-Item (Join-Path $PSScriptRoot "mcp\android_device_mcp.py") (Join-Path $AssetsDir "android_device_mcp.py")
Copy-Item (Join-Path $PSScriptRoot "codex-android.config.toml") (Join-Path $AssetsDir "codex-android.config.toml")
if (-not (Test-Path $CodexBinary)) {
    throw "Codex Android binary was not found: $CodexBinary. Run .\android\build-android.ps1 first."
}
Copy-Item $CodexBinary (Join-Path $AssetsDir "codex")
Copy-Item $CodexBinary (Join-Path $NativeLibDir "libcodex.so")

& $Aapt2 compile --dir $ResDir -o $CompiledRes
if ($LASTEXITCODE -ne 0) {
    throw "aapt2 compile failed with exit code $LASTEXITCODE"
}

& $Aapt2 link `
    -o $UnsignedApk `
    -I $AndroidJar `
    --manifest $Manifest `
    --java $GenDir `
    -A $AssetsDir `
    --min-sdk-version 26 `
    --target-sdk-version 35 `
    --version-code 1 `
    --version-name "0.1.0" `
    $CompiledRes
if ($LASTEXITCODE -ne 0) {
    throw "aapt2 link failed with exit code $LASTEXITCODE"
}

$Sources = @(Get-ChildItem $SrcDir, $GenDir -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& $Javac -encoding UTF-8 -source 17 -target 17 -classpath $AndroidJar -d $ClassesDir @Sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

$ClassFiles = @(Get-ChildItem $ClassesDir -Recurse -Filter *.class | ForEach-Object { $_.FullName })
& $D8 --min-api 26 --output $DexDir @ClassFiles
if ($LASTEXITCODE -ne 0) {
    throw "d8 failed with exit code $LASTEXITCODE"
}

Push-Location $DexDir
try {
    & $Jar uf $UnsignedApk "classes.dex"
} finally {
    Pop-Location
}
if ($LASTEXITCODE -ne 0) {
    throw "jar update classes.dex failed with exit code $LASTEXITCODE"
}

Push-Location (Join-Path $OutDir "native-lib")
try {
    & $Jar uf $UnsignedApk "lib/$Abi/libcodex.so"
} finally {
    Pop-Location
}
if ($LASTEXITCODE -ne 0) {
    throw "jar update native Codex binary failed with exit code $LASTEXITCODE"
}

& $Zipalign -f 4 $UnsignedApk $AlignedApk
if ($LASTEXITCODE -ne 0) {
    throw "zipalign failed with exit code $LASTEXITCODE"
}

if (-not (Test-Path $Keystore)) {
    & $Keytool -genkeypair `
        -keystore $Keystore `
        -storepass android `
        -keypass android `
        -alias androiddebugkey `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -dname "CN=Android Debug,O=Codex,C=US"
    if ($LASTEXITCODE -ne 0) {
        throw "keytool failed with exit code $LASTEXITCODE"
    }
}

& $ApkSigner sign `
    --ks $Keystore `
    --ks-pass pass:android `
    --key-pass pass:android `
    --out $SignedApk `
    $AlignedApk
if ($LASTEXITCODE -ne 0) {
    throw "apksigner failed with exit code $LASTEXITCODE"
}

& $ApkSigner verify --verbose $SignedApk
if ($LASTEXITCODE -ne 0) {
    throw "apksigner verify failed with exit code $LASTEXITCODE"
}

Write-Host "Built APK:"
Write-Host (Resolve-Path $SignedApk)
