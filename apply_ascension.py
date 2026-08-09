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

    # Remove upstream launcher icons with the same Android resource name.
    # Android treats ic_launcher.png and ic_launcher.webp as duplicate resources.
    for old_icon in (
        "ic_launcher.webp",
        "ic_launcher_round.webp",
        "ic_launcher.png",
        "ic_launcher_round.png",
    ):
        old_path = target_dir / old_icon
        if old_path.exists():
            old_path.unlink()

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

# 7. Ascension direct nickname mode (no account chooser UI)
# Keep Amethyst's internal local-profile mechanism, but present it as a simple Nick flow.

# Hide the account spinner/header from the launcher. The view stays instantiated internally so
# existing account-selection/game-launch code continues working, but users never see "Adicionar conta".
activity_layout_path = required("app_pojavlauncher/src/main/res/layout/activity_pojav_launcher.xml")
activity_layout = activity_layout_path.read_text(encoding="utf-8")
activity_layout = replace_required(
    activity_layout,
    '''        android:id="@+id/account_spinner"
        android:layout_width="match_parent"''',
    '''        android:id="@+id/account_spinner"
        android:visibility="gone"
        android:layout_width="match_parent"''',
    "ocultar seletor de contas"
)
activity_layout_path.write_text(activity_layout, encoding="utf-8")

# Allow Amethyst's local nickname profile without requiring a previously-added Microsoft profile.
local_login_path = required(
    "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/LocalLoginFragment.java"
)
local_login = local_login_path.read_text(encoding="utf-8")
local_login = local_login.replace(
    "import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;\n\n",
    ""
)
local_login = replace_required(
    local_login,
    '''        // This is overkill but meh
        if (!hasOnlineProfile()){
            Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
        }
''',
    "",
    "liberar Nick local sem conta Microsoft"
)
local_login_path.write_text(local_login, encoding="utf-8")

# Replace the generic local-account page with a branded, Nick-only screen.
local_login_layout_path = required("app_pojavlauncher/src/main/res/layout/fragment_local_login.xml")
local_login_layout_path.write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#07090B"
    android:padding="22dp">

    <LinearLayout
        android:id="@+id/login_menu"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center_horizontal"
        android:padding="24dp"
        android:background="@drawable/ascension_panel"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent">

        <ImageView
            android:layout_width="72dp"
            android:layout_height="72dp"
            android:src="@drawable/ascension_icon_foreground"
            android:contentDescription="Ascension Pixelmon" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="14dp"
            android:text="ESCOLHA SEU NICK"
            android:textColor="#FFFFFF"
            android:textStyle="bold"
            android:textSize="22sp" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:gravity="center"
            android:text="Seu Nick é sua identidade no servidor. Use sempre o mesmo Nick para manter seu progresso."
            android:textColor="#9BA6AE"
            android:textSize="13sp" />

        <com.kdt.mcgui.MineEditText
            android:id="@+id/login_edit_email"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:layout_marginTop="22dp"
            android:hint="Digite seu Nick"
            android:imeOptions="actionDone|flagNoExtractUi"
            android:inputType="text"
            android:maxLength="16"
            android:singleLine="true"
            android:textSize="16sp" />

        <com.kdt.mcgui.MineButton
            android:id="@+id/login_button"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:layout_marginTop="14dp"
            android:text="CONTINUAR"
            android:textColor="@android:color/white" />

    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
''', encoding="utf-8")

# Route every "choose authentication" request straight to the Nick screen instead of the
# Microsoft Account / Local Account selector. Also open the Nick screen automatically once,
# on a clean installation where no nickname profile has been saved yet.
launcher_activity_path = required(
    "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/LauncherActivity.java"
)
launcher_activity = launcher_activity_path.read_text(encoding="utf-8")

local_import = "import net.kdt.pojavlaunch.fragments.LocalLoginFragment;\n"
if local_import not in launcher_activity:
    import_anchor = "import net.kdt.pojavlaunch.fragments.MainMenuFragment;\n"
    if import_anchor not in launcher_activity:
        raise SystemExit("[Ascension] Nao foi possivel inserir import do Nick local.")
    launcher_activity = launcher_activity.replace(import_anchor, import_anchor + local_import, 1)

launcher_activity = replace_required(
    launcher_activity,
    "        Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);",
    "        Tools.swapFragment(this, LocalLoginFragment.class, LocalLoginFragment.TAG, null);",
    "pular tela Microsoft/Local Account"
)

launcher_activity = replace_required(
    launcher_activity,
    '''        if(mAccountSpinner.getSelectedAccount() == null){
            Toast.makeText(this, R.string.no_saved_accounts, Toast.LENGTH_LONG).show();
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
            return false;
        }
''',
    '''        if(mAccountSpinner.getSelectedAccount() == null){
            Tools.swapFragment(this, LocalLoginFragment.class, LocalLoginFragment.TAG, null);
            return false;
        }
''',
    "remover aviso de contas salvas ao jogar"
)

launcher_activity = replace_required(
    launcher_activity,
    '''        bindViews();
        checkNotificationPermission();''',
    '''        bindViews();

        // Ascension: first launch asks only for the player's Nick. Once saved, this is skipped.
        mFragmentView.post(() -> {
            if (mAccountSpinner.getSelectedAccount() == null) {
                Tools.swapFragment(this, LocalLoginFragment.class, LocalLoginFragment.TAG, null);
            }
        });

        checkNotificationPermission();''',
    "abrir tela de Nick apenas na primeira instalacao"
)

launcher_activity_path.write_text(launcher_activity, encoding="utf-8")

print("[Ascension] Modo Nick direto aplicado: seletor de contas removido da interface.")

