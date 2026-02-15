param(
    [string]$NodeJsMobileVersion = "18.20.4",
    [switch]$SkipNpmInstall
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$androidDir = Join-Path $repoRoot "android"
$appDir = Join-Path $androidDir "app"
$assetsAppDir = Join-Path $appDir "src/main/assets/app"
$libnodeDir = Join-Path $appDir "libnode"
$zipPath = Join-Path $repoRoot "tools/nodejs-mobile-v$NodeJsMobileVersion-android.zip"
$tempExtractDir = Join-Path $repoRoot "tools/nodejs-mobile-extract"

if (-not (Test-Path $androidDir)) {
    throw "android project not found at $androidDir"
}

if (-not $SkipNpmInstall) {
    if (-not (Test-Path (Join-Path $repoRoot "node_modules"))) {
        Write-Host "[1/4] Installing production dependencies..."
        Push-Location $repoRoot
        npm ci --omit=dev
        Pop-Location
    }
}

Write-Host "[2/4] Downloading nodejs-mobile v$NodeJsMobileVersion..."
$downloadUrl = "https://github.com/nodejs-mobile/nodejs-mobile/releases/download/v$NodeJsMobileVersion/nodejs-mobile-v$NodeJsMobileVersion-android.zip"
New-Item -ItemType Directory -Force (Split-Path -Parent $zipPath) | Out-Null
if (-not (Test-Path $zipPath)) {
    Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath
}

if (Test-Path $tempExtractDir) {
    Remove-Item -Recurse -Force $tempExtractDir
}
Expand-Archive -Path $zipPath -DestinationPath $tempExtractDir -Force

if (Test-Path $libnodeDir) {
    Remove-Item -Recurse -Force $libnodeDir
}
New-Item -ItemType Directory -Force $libnodeDir | Out-Null
Copy-Item -Recurse -Force (Join-Path $tempExtractDir "bin") (Join-Path $libnodeDir "bin")
Copy-Item -Recurse -Force (Join-Path $tempExtractDir "include") (Join-Path $libnodeDir "include")

Write-Host "[3/4] Staging SillyTavern files into Android assets..."
if (Test-Path $assetsAppDir) {
    Remove-Item -Recurse -Force $assetsAppDir
}
New-Item -ItemType Directory -Force $assetsAppDir | Out-Null

$copyItems = @(
    "default",
    "public",
    "src",
    "plugins",
    "data",
    "server.js",
    "package.json",
    "package-lock.json",
    "plugins.js",
    "post-install.js",
    "recover.js"
)

foreach ($item in $copyItems) {
    $sourcePath = Join-Path $repoRoot $item
    if (-not (Test-Path $sourcePath)) {
        throw "Required file or directory missing: $item"
    }
    Copy-Item -Recurse -Force $sourcePath $assetsAppDir
}

if (-not (Test-Path (Join-Path $repoRoot "node_modules"))) {
    throw "node_modules not found. Run npm ci first or remove -SkipNpmInstall."
}
Copy-Item -Recurse -Force (Join-Path $repoRoot "node_modules") $assetsAppDir
Copy-Item -Force (Join-Path $appDir "src/main/assets/launcher.mjs") $assetsAppDir

Write-Host "[4/4] Done."
Write-Host "Now open android/ in Android Studio and run Build APK (assembleDebug)."
