# Ascension Pixelmon Mobile — Pojav/Amethyst Edition

This package turns a pinned Amethyst/Pojav Android source tree into the **Ascension Pixelmon Launcher**.

## What is already implemented

- Ascension branded WebView launcher UI.
- Local Nick profile (`3–16` letters/numbers/underscore), with deterministic offline UUID.
- Fixed Minecraft **1.21.1**.
- Patched Amethyst downloader so the Ascension flow can install/verify Minecraft 1.21.1 while a **local Nick** is selected. The normal upstream `start()` behavior remains unchanged outside the Ascension flow.
- Java runtime installation remains handled by Amethyst's Minecraft downloader/runtime logic.
- Fixed NeoForge **21.1.200**, downloaded from NeoForge's Maven using Amethyst's native `NeoForgeDownloadTask`.
- NeoForge installer runs in Amethyst's separate `:gui_installer` process and returns to the launcher when complete.
- Dedicated launcher profile: `Ascension Pixelmon`.
- Dedicated game directory: `ascension-pixelmon`.
- Automatic `mods.zip` update using GitHub release asset SHA-256 when available.
- Atomic mods replacement: `mods.stage` → validate → preserve old `mods` → commit → delete backup. On failure, the previous mods folder is restored.
- `config.zip` and `options.txt` are installed only during initial client setup and preserved on later launches.
- Bundled `Ascension-CleanMenu-1.0.0.jar` is restored to the mods folder if missing.
- `servers.dat` is created with `Jogar.AscensionPixelmon.com.br` if one is not already present.
- `JOGAR` flow: Minecraft → NeoForge → modpack → game.
- App ID changed to `br.com.ascensionpixelmon.launcher` (`.debug` for debug build).
- Ascension launcher icon and name.

## Tested base

The patcher is pinned to:

```text
AngelAuraMC/Amethyst-Android
4cf805a93124269b47f8a4ba27fcce36b79ab5ef
```

It intentionally refuses to patch another commit unless `--allow-unpinned` is supplied. This prevents silent source drift from breaking the launcher.

## Easiest way to get the APK: GitHub Actions

1. Create a new GitHub repository and upload **the contents of this ZIP**.
2. Open the repository's **Actions** tab.
3. Run **Build Ascension Pixelmon APK**.
4. When the workflow finishes, download the artifact named:

```text
Ascension-Pixelmon-Mobile-Debug
```

Inside it:

```text
Ascension-Pixelmon-Mobile-Debug.apk
Ascension-Pixelmon-Mobile-Debug.sha256
```

That debug APK is appropriate for the first BlueStacks/Android tests.

## Local build

Prerequisites include Git, Python 3, Android SDK/NDK compatible with upstream Amethyst, JDK 21, Gradle 9.6.1, and the upstream JRE asset expected by Amethyst.

Linux/macOS:

```bash
./build-local.sh
```

Windows PowerShell:

```powershell
./build-local.ps1
```

The GitHub Actions route is recommended because it reproduces Amethyst's own build setup and downloads the required upstream JRE artifact automatically.

## Important files

- `tools/apply_ascension.py` — deterministic patcher.
- `overlay/.../fragments/MainMenuFragment.java` — Ascension launcher UI/bridge and launch orchestration.
- `overlay/.../ascension/AscensionBootstrap.java` — Minecraft + NeoForge bootstrap state machine.
- `overlay/.../ascension/AscensionUpdater.java` — safe modpack updater.
- `overlay/.../assets/ui/` — Ascension UI and images.
- `.github/workflows/build-ascension-apk.yml` — automatic debug APK build.

## Modpack URLs

The integration currently uses:

```text
https://github.com/uwillzard/ascension-pixelmon-modpack/releases/latest/download/mods.zip
https://github.com/uwillzard/ascension-pixelmon-modpack/releases/latest/download/config.zip
https://github.com/uwillzard/ascension-pixelmon-modpack/releases/latest/download/options.txt
```

and checks the latest release API for the `mods.zip` asset digest.

## First-run behavior

On the first JOGAR:

1. Save/create the local Nick profile.
2. Download/verify Minecraft 1.21.1.
3. Let Amethyst install the required Java runtime.
4. Download NeoForge 21.1.200 installer if missing.
5. Run the NeoForge client installer.
6. Create/select the fixed Ascension launcher profile.
7. Download and atomically install `mods.zip`.
8. Install initial `config.zip` + `options.txt` once.
9. Start the NeoForge Minecraft version.

Later JOGAR operations skip already-installed game/bootstrap components and only replace the mods folder when the remote digest changes (or a repair was requested).

## Current validation level

The overlay, XML, workflow, patcher and source invariants are validated in this package. The actual Android APK compilation must run against the full pinned Amethyst source tree; that full upstream repository is intentionally not bundled here because it is very large.


## v0.3
- Corrige crash de WebView nula usando um layout exclusivo `fragment_ascension_launcher`.
