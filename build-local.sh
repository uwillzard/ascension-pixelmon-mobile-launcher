#!/usr/bin/env bash
set -euo pipefail
BASE="${1:-Amethyst-Android}"
PIN="4cf805a93124269b47f8a4ba27fcce36b79ab5ef"
if [[ ! -d "$BASE/.git" ]]; then
  git clone --recurse-submodules https://github.com/AngelAuraMC/Amethyst-Android.git "$BASE"
fi
git -C "$BASE" checkout "$PIN"
git -C "$BASE" submodule update --init --recursive
python3 tools/apply_ascension.py "$BASE"
(
  cd "$BASE"
  ./scripts/languagelist_updater.sh
  gradle :app_pojavlauncher:assembleDebug
)
echo "APK: $BASE/app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk"
