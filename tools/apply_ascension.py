#!/usr/bin/env python3
from __future__ import annotations

import argparse
import shutil
import subprocess
from pathlib import Path

PINNED_COMMIT = "4cf805a93124269b47f8a4ba27fcce36b79ab5ef"


def fail(msg: str) -> None:
    raise SystemExit("[Ascension patch] " + msg)


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        fail(f"esperava 1 ocorrência em {path}, encontrei {count}: {old[:90]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_all_required(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        fail(f"padrão não encontrado em {path}: {old!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")


def git_head(root: Path) -> str | None:
    try:
        return subprocess.check_output(
            ["git", "-C", str(root), "rev-parse", "HEAD"], text=True, stderr=subprocess.DEVNULL
        ).strip()
    except Exception:
        return None


def patch_minecraft_downloader(root: Path) -> None:
    path = root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/tasks/MinecraftDownloader.java"
    marker = "    private void downloadGame(Activity activity, JMinecraftVersionList.Version verInfo, String versionName) throws Exception {"
    method = r'''    /**
     * Ascension-only entry point: downloads/verifies public Minecraft game files even when
     * the selected account is a local Nick. The normal Amethyst start() behavior is kept
     * unchanged for every other launcher flow.
     */
    public void startForcedDownload(@NonNull Activity activity,
                                    @Nullable JMinecraftVersionList.Version version,
                                    @NonNull String realVersion,
                                    @NonNull AsyncMinecraftDownloader.DoneListener listener) {
        isOnline = Tools.isOnline(activity);
        Tools.switchDemo(false);
        sExecutorService.execute(() -> {
            try {
                if (!isOnline) {
                    throw new IOException("Sem conexão com a internet para instalar Minecraft " + realVersion);
                }
                downloadGame(activity, version, realVersion);
                listener.onDownloadDone();
            } catch (Exception e) {
                listener.onDownloadFailed(e);
            }
            ProgressLayout.clearProgress(ProgressLayout.DOWNLOAD_MINECRAFT);
        });
    }

'''
    text = path.read_text(encoding="utf-8")
    if "startForcedDownload(" in text:
        return
    if text.count(marker) != 1:
        fail("não encontrei o ponto de inserção de startForcedDownload em MinecraftDownloader.java")
    path.write_text(text.replace(marker, method + marker, 1), encoding="utf-8")


def patch_jre_utils(root: Path) -> None:
    path = root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java"
    old = '''        Tools.fullyExit();\n    }\n\n    /**\n     *  Gives an argument list filled with both the user args'''
    new = '''        // Ascension invokes the NeoForge installer from inside the launcher. In this special\n        // flow, return to LauncherActivity instead of terminating the entire launcher process.\n        if (activity.getIntent().getBooleanExtra("ascension_return_after_vm", false)) {\n            activity.runOnUiThread(() -> {\n                activity.setResult(Activity.RESULT_OK);\n                activity.finish();\n            });\n            return;\n        }\n        Tools.fullyExit();\n    }\n\n    /**\n     *  Gives an argument list filled with both the user args'''
    text = path.read_text(encoding="utf-8")
    if "ascension_return_after_vm" in text:
        return
    if old not in text:
        fail("não encontrei o Tools.fullyExit() esperado em JREUtils.java")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def patch_branding(root: Path) -> None:
    gradle = root / "app_pojavlauncher/build.gradle"
    replacements = [
        ('applicationId "org.angelauramc.amethyst"', 'applicationId "br.com.ascensionpixelmon.launcher"'),
        ('resValue "string", "app_name", "Amethyst (Debug)"', 'resValue "string", "app_name", "Ascension Pixelmon (Debug)"'),
        ('resValue "string", "app_short_name", "Amethyst (Debug)"', 'resValue "string", "app_short_name", "Ascension Pixelmon"'),
        ("resValue 'string', 'application_package', 'org.angelauramc.amethyst.debug'", "resValue 'string', 'application_package', 'br.com.ascensionpixelmon.launcher.debug'"),
        ("resValue 'string', 'storageProviderAuthorities', 'org.angelauramc.amethyst.scoped.gamefolder.debug'", "resValue 'string', 'storageProviderAuthorities', 'br.com.ascensionpixelmon.launcher.scoped.gamefolder.debug'"),
        ("resValue 'string', 'shareProviderAuthority', 'org.angelauramc.amethyst.scoped.controlfolder.debug'", "resValue 'string', 'shareProviderAuthority', 'br.com.ascensionpixelmon.launcher.scoped.controlfolder.debug'"),
        ('resValue "string", "app_name", "Amethyst"', 'resValue "string", "app_name", "Ascension Pixelmon"'),
        ('resValue "string", "app_short_name", "Amethyst"', 'resValue "string", "app_short_name", "Ascension Pixelmon"'),
        ("resValue 'string', 'storageProviderAuthorities', 'org.angelauramc.amethyst.scoped.gamefolder'", "resValue 'string', 'storageProviderAuthorities', 'br.com.ascensionpixelmon.launcher.scoped.gamefolder'"),
        ("resValue 'string', 'application_package', 'org.angelauramc.amethyst'", "resValue 'string', 'application_package', 'br.com.ascensionpixelmon.launcher'"),
    ]
    text = gradle.read_text(encoding="utf-8")
    for old, new in replacements:
        if old not in text:
            # It is fine when the script is re-run on an already patched tree.
            if new in text:
                continue
            fail(f"branding pattern ausente em build.gradle: {old}")
        text = text.replace(old, new)
    gradle.write_text(text, encoding="utf-8")

    tools = root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/Tools.java"
    text = tools.read_text(encoding="utf-8")
    pairs = [
        ('public static String APP_NAME = "Amethyst";', 'public static String APP_NAME = "Ascension Pixelmon";'),
        ('public static final String URL_HOME = "https://wiki.angelauramc.dev";', 'public static final String URL_HOME = "https://AscensionPixelmon.com.br";'),
        ('/games/Amethyst', '/games/AscensionPixelmon'),
        ('"games/Amethyst"', '"games/AscensionPixelmon"'),
    ]
    for old, new in pairs:
        if old in text:
            text = text.replace(old, new)
    tools.write_text(text, encoding="utf-8")


def clean_launcher_icons(root: Path) -> None:
    res = root / "app_pojavlauncher/src/main/res"
    if not res.exists():
        return
    for directory in res.glob("mipmap*"):
        if not directory.is_dir():
            continue
        for pattern in ("ic_launcher.*", "ic_launcher_round.*"):
            for file in directory.glob(pattern):
                file.unlink()


def copy_overlay(repo_root: Path, source_root: Path) -> None:
    overlay = repo_root / "overlay"
    if not overlay.is_dir():
        fail("pasta overlay não encontrada")
    for src in overlay.rglob("*"):
        if src.is_dir():
            continue
        rel = src.relative_to(overlay)
        dst = source_root / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)


def main() -> None:
    parser = argparse.ArgumentParser(description="Aplica o Ascension Pixelmon Mobile sobre Amethyst-Android")
    parser.add_argument("source", type=Path, help="pasta do clone AngelAuraMC/Amethyst-Android")
    parser.add_argument("--allow-unpinned", action="store_true", help="permite aplicar fora do commit testado")
    args = parser.parse_args()

    source = args.source.resolve()
    repo_root = Path(__file__).resolve().parents[1]
    if not (source / "app_pojavlauncher/build.gradle").is_file():
        fail(f"{source} não parece ser um clone do Amethyst-Android")

    head = git_head(source)
    if head and head != PINNED_COMMIT and not args.allow_unpinned:
        fail(f"commit {head} não é o commit testado {PINNED_COMMIT}. Faça git checkout {PINNED_COMMIT}")

    patch_minecraft_downloader(source)
    patch_jre_utils(source)
    patch_branding(source)
    clean_launcher_icons(source)
    copy_overlay(repo_root, source)

    print("[Ascension patch] OK")
    print("Base:", head or "sem git")
    print("Minecraft: 1.21.1 | NeoForge: 21.1.200")
    print("Build: gradle :app_pojavlauncher:assembleDebug")


if __name__ == "__main__":
    main()
