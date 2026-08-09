param([string]$Base = "Amethyst-Android")
$ErrorActionPreference = "Stop"
$Pin = "4cf805a93124269b47f8a4ba27fcce36b79ab5ef"
if (-not (Test-Path "$Base/.git")) {
  git clone --recurse-submodules https://github.com/AngelAuraMC/Amethyst-Android.git $Base
}
git -C $Base checkout $Pin
git -C $Base submodule update --init --recursive
python tools/apply_ascension.py $Base
Push-Location $Base
./scripts/languagelist_updater.sh
gradle :app_pojavlauncher:assembleDebug
Pop-Location
Write-Host "APK: $Base/app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk"
