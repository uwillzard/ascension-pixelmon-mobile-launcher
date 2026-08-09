$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Source = Join-Path $Root "amethyst"
$Dist = Join-Path $Root "dist"

if (!(Get-Command git -ErrorAction SilentlyContinue)) {
    throw "Git nao encontrado."
}
if (!(Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java 21 nao encontrado."
}
if (!(Get-Command python -ErrorAction SilentlyContinue)) {
    throw "Python nao encontrado."
}

if (Test-Path $Source) {
    Remove-Item -Recurse -Force $Source
}

git clone --depth 1 --branch v3_openjdk --recurse-submodules --shallow-submodules `
  https://github.com/AngelAuraMC/Amethyst-Android.git $Source

python (Join-Path $Root "apply_ascension.py") $Source

Push-Location $Source
try {
    .\gradlew.bat :app_pojavlauncher:assembleDebug --stacktrace
} finally {
    Pop-Location
}

New-Item -ItemType Directory -Force -Path $Dist | Out-Null

$Apk = Get-ChildItem -Recurse `
  (Join-Path $Source "app_pojavlauncher\build\outputs\apk\debug") `
  -Filter *.apk | Select-Object -First 1

if ($null -eq $Apk) {
    throw "APK nao encontrado apos o build."
}

$Final = Join-Path $Dist "Ascension-Pixelmon-Mobile-v1-debug.apk"
Copy-Item $Apk.FullName $Final -Force

Write-Host ""
Write-Host "APK pronto:"
Write-Host $Final
