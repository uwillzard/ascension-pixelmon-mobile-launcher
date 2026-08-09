#!/usr/bin/env python3
from pathlib import Path
import re
import shutil
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else "amethyst").resolve()
HERE = Path(__file__).resolve().parent

def required(relative):
    path = ROOT / relative
    if not path.exists():
        raise SystemExit(f"[Ascension] Arquivo obrigatorio nao encontrado: {path}")
    return path

def replace_required(text, old, new, label):
    if old not in text:
        raise SystemExit(f"[Ascension] Patch nao encontrou: {label}")
    return text.replace(old, new, 1)

# 1. Package / visible application branding
gradle_path = required("app_pojavlauncher/build.gradle")
gradle = gradle_path.read_text(encoding="utf-8")

gradle = replace_required(
    gradle,
    'applicationId "org.angelauramc.amethyst"',
    'applicationId "br.com.ascensionpixelmon.launcher"',
    "applicationId"
)
gradle = gradle.replace(
    'resValue "string", "app_name", "Amethyst (Debug)"',
    'resValue "string", "app_name", "Ascension Pixelmon Launcher"'
)
gradle = gradle.replace(
    'resValue "string", "app_short_name", "Amethyst (Debug)"',
    'resValue "string", "app_short_name", "Ascension Pixelmon"'
)
gradle = gradle.replace(
    "resValue 'string', 'application_package', 'org.angelauramc.amethyst.debug'",
    "resValue 'string', 'application_package', 'br.com.ascensionpixelmon.launcher.debug'"
)
gradle = gradle.replace(
    "resValue 'string', 'storageProviderAuthorities', 'org.angelauramc.amethyst.scoped.gamefolder.debug'",
    "resValue 'string', 'storageProviderAuthorities', 'br.com.ascensionpixelmon.launcher.scoped.gamefolder.debug'"
)
gradle = gradle.replace(
    "resValue 'string', 'shareProviderAuthority', 'org.angelauramc.amethyst.scoped.controlfolder.debug'",
    "resValue 'string', 'shareProviderAuthority', 'br.com.ascensionpixelmon.launcher.scoped.controlfolder.debug'"
)
gradle = gradle.replace(
    'resValue "string", "app_name", "Amethyst"',
    'resValue "string", "app_name", "Ascension Pixelmon Launcher"'
)
gradle = gradle.replace(
    'resValue "string", "app_short_name", "Amethyst"',
    'resValue "string", "app_short_name", "Ascension Pixelmon"'
)
gradle = gradle.replace(
    "resValue 'string', 'storageProviderAuthorities', 'org.angelauramc.amethyst.scoped.gamefolder'",
    "resValue 'string', 'storageProviderAuthorities', 'br.com.ascensionpixelmon.launcher.scoped.gamefolder'"
)
gradle = gradle.replace(
    "resValue 'string', 'application_package', 'org.angelauramc.amethyst'",
    "resValue 'string', 'application_package', 'br.com.ascensionpixelmon.launcher'"
)
gradle_path.write_text(gradle, encoding="utf-8")

# 2. Launcher identity, site, and old-Android folder name
tools_path = required("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/Tools.java")
tools = tools_path.read_text(encoding="utf-8")
tools = replace_required(
    tools,
    'public static String APP_NAME = "Amethyst";',
    'public static String APP_NAME = "Ascension Pixelmon";',
    "Tools.APP_NAME"
)
tools = replace_required(
    tools,
    'public static final String URL_HOME = "https://wiki.angelauramc.dev";',
    'public static final String URL_HOME = "https://AscensionPixelmon.com.br";',
    "Tools.URL_HOME"
)
tools = tools.replace("/games/Amethyst", "/games/AscensionPixelmon")
tools = tools.replace('"games/Amethyst"', '"games/AscensionPixelmon"')
tools_path.write_text(tools, encoding="utf-8")

# 3. Discord invite in all translations containing the resource
res_root = required("app_pojavlauncher/src/main/res")
discord_patched = 0
for strings_path in res_root.glob("values*/strings.xml"):
    raw = strings_path.read_text(encoding="utf-8")
    changed, count = re.subn(
        r'(<string\s+name="discord_invite"[^>]*>).*?(</string>)',
        r'\1https://discord.gg/HuF2JHbSZr\2',
        raw,
        flags=re.DOTALL
    )
    if count:
        strings_path.write_text(changed, encoding="utf-8")
        discord_patched += count
if discord_patched == 0:
    raise SystemExit("[Ascension] Recurso discord_invite nao encontrado.")

# 4. Main launcher screen + icons
launcher_layout = required("app_pojavlauncher/src/main/res/layout/fragment_launcher.xml")
launcher_layout.write_text(
    (HERE / "branding" / "fragment_launcher.xml").read_text(encoding="utf-8"),
    encoding="utf-8"
)

drawable = res_root / "drawable"
drawable.mkdir(parents=True, exist_ok=True)
for filename in (
    "ascension_panel.xml",
    "ascension_icon_foreground.xml",
    "ascension_icon_background.xml",
):
    shutil.copy2(HERE / "branding" / filename, drawable / filename)

adaptive_dir = res_root / "mipmap-anydpi-v26"
adaptive_dir.mkdir(parents=True, exist_ok=True)
adaptive_xml = (HERE / "branding" / "ic_launcher.xml").read_text(encoding="utf-8")
(adaptive_dir / "ic_launcher.xml").write_text(adaptive_xml, encoding="utf-8")
(adaptive_dir / "ic_launcher_round.xml").write_text(adaptive_xml, encoding="utf-8")

for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
    target_dir = res_root / f"mipmap-{density}"
    target_dir.mkdir(parents=True, exist_ok=True)
    source = HERE / "branding" / f"ic_launcher_{density}.png"
    if source.exists():
        shutil.copy2(source, target_dir / "ic_launcher.png")
        shutil.copy2(source, target_dir / "ic_launcher_round.png")

play_store_src = HERE / "branding" / "ic_launcher-playstore.png"
play_store_dst = ROOT / "app_pojavlauncher" / "src" / "main" / "ic_launcher-playstore.png"
if play_store_src.exists():
    shutil.copy2(play_store_src, play_store_dst)

# 5. Add Ascension updater
ascension_java_dir = (
    ROOT / "app_pojavlauncher" / "src" / "main" / "java"
    / "net" / "kdt" / "pojavlaunch" / "ascension"
)
ascension_java_dir.mkdir(parents=True, exist_ok=True)
shutil.copy2(
    HERE / "branding" / "AscensionModpackUpdater.java",
    ascension_java_dir / "AscensionModpackUpdater.java"
)

# 6. Hook the updater into JOGAR
main_path = required(
    "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java"
)
main = main_path.read_text(encoding="utf-8")

import_line = "import net.kdt.pojavlaunch.ascension.AscensionModpackUpdater;\n"
if import_line not in main:
    import_anchor = "import net.kdt.pojavlaunch.Tools;\n"
    if import_anchor not in main:
        raise SystemExit("[Ascension] Nao foi possivel inserir import do updater.")
    main = main.replace(import_anchor, import_anchor + import_line, 1)

old_play_handler = '''        mPlayButton.setOnClickListener(v -> {
            if (Tools.hasMods("sodium") && !(LauncherPreferences.DEFAULT_PREF.getBoolean("sodium_override", false))) {
                AlertDialog sodiumWarningDialog = new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.sodium_warning_title)
                        .setMessage(R.string.sodium_warning_message)
                        .setNeutralButton(R.string.delete_sodium, (d,w)-> {
                            Tools.deleteSodiumMods();
                            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                        })
                        .create();
                sodiumWarningDialog.show();
            } else ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });
'''

new_play_handler = '''        mPlayButton.setOnClickListener(v -> {
            mPlayButton.setEnabled(false);

            AscensionModpackUpdater.updateBeforeLaunch(
                    requireContext(),
                    getCurrentProfileDirectory(),
                    new AscensionModpackUpdater.Listener() {
                        @Override
                        public void onReady() {
                            if (!isAdded()) return;
                            mPlayButton.setEnabled(true);
                            launchAscensionGame();
                        }

                        @Override
                        public void onError(String message) {
                            if (!isAdded()) return;
                            mPlayButton.setEnabled(true);
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Ascension Pixelmon")
                                    .setMessage(
                                            "A atualizacao nao foi instalada. "
                                                    + "Seus mods antigos foram preservados.\\n\\n"
                                                    + message
                                    )
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        }
                    }
            );
        });
'''

if old_play_handler not in main:
    raise SystemExit("[Ascension] Handler atual do botao JOGAR nao foi encontrado.")
main = main.replace(old_play_handler, new_play_handler, 1)

helper = '''    private void launchAscensionGame() {
        if (Tools.hasMods("sodium")
                && !(LauncherPreferences.DEFAULT_PREF.getBoolean("sodium_override", false))) {
            AlertDialog sodiumWarningDialog = new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.sodium_warning_title)
                    .setMessage(R.string.sodium_warning_message)
                    .setNeutralButton(R.string.delete_sodium, (d, w) -> {
                        Tools.deleteSodiumMods();
                        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                    })
                    .create();
            sodiumWarningDialog.show();
        } else {
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        }
    }

'''

profile_method = "    private File getCurrentProfileDirectory() {"
if profile_method not in main:
    raise SystemExit("[Ascension] Metodo de pasta do perfil nao encontrado.")
if "private void launchAscensionGame()" not in main:
    main = main.replace(profile_method, helper + profile_method, 1)

main_path.write_text(main, encoding="utf-8")
print("[Ascension] Patches aplicados com sucesso em:", ROOT)
