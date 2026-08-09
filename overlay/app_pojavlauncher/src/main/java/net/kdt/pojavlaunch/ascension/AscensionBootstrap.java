package net.kdt.pojavlaunch.ascension;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener;
import net.kdt.pojavlaunch.modloaders.NeoForgeDownloadTask;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AscensionBootstrap {
    public interface Listener {
        void onStatus(String message, int percent);
        void onNeoForgeInstallerReady(File installer);
        void onReady(String neoForgeVersionId, boolean launchAfter);
        void onError(String message, Throwable error);
    }

    private final Activity activity;
    private final Listener listener;
    private final SharedPreferences prefs;

    public AscensionBootstrap(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.prefs = activity.getSharedPreferences(AscensionConfig.PREFS, Context.MODE_PRIVATE);
    }

    public void start(String nick, boolean launchAfter) {
        if (nick == null || !nick.trim().matches("^[A-Za-z0-9_]{3,16}$")) {
            listener.onError("Escolha um Nick de 3 a 16 caracteres.", null);
            return;
        }
        final String cleanNick = nick.trim();
        listener.onStatus("Preparando perfil do treinador...", 3);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                ensureLocalAccount(cleanNick, null);
                ensureMinecraft(cleanNick, launchAfter);
            } catch (Throwable t) {
                listener.onError(cleanMessage(t), t);
            }
        });
    }

    private void ensureMinecraft(String nick, boolean launchAfter) {
        boolean bootstrapDone = prefs.getBoolean("minecraft_1211_bootstrapped", false);
        if (bootstrapDone && isMinecraftInstalled()) {
            listener.onStatus("Minecraft 1.21.1 instalado.", 32);
            ensureNeoForge(nick, launchAfter);
            return;
        }

        listener.onStatus("Verificando Minecraft 1.21.1...", 8);
        new AsyncVersionList().getVersionList(versionList -> {
            try {
                if (versionList == null || versionList.versions == null) {
                    throw new IllegalStateException("não foi possível carregar a lista de versões do Minecraft");
                }
                JMinecraftVersionList.Version target = null;
                for (JMinecraftVersionList.Version version : versionList.versions) {
                    if (AscensionConfig.MC_VERSION.equals(version.id)) {
                        target = version;
                        break;
                    }
                }
                if (target == null) throw new IllegalStateException("Minecraft 1.21.1 não foi encontrado no manifesto oficial");

                listener.onStatus("Instalando/verificando Minecraft 1.21.1 e Java 21...", 12);
                new MinecraftDownloader().startForcedDownload(
                        activity,
                        target,
                        AscensionConfig.MC_VERSION,
                        new AsyncMinecraftDownloader.DoneListener() {
                            @Override
                            public void onDownloadDone() {
                                prefs.edit().putBoolean("minecraft_1211_bootstrapped", true).apply();
                                listener.onStatus("Minecraft 1.21.1 pronto.", 35);
                                ensureNeoForge(nick, launchAfter);
                            }

                            @Override
                            public void onDownloadFailed(Throwable throwable) {
                                listener.onError("Falha ao instalar Minecraft 1.21.1: " + cleanMessage(throwable), throwable);
                            }
                        }
                );
            } catch (Throwable t) {
                listener.onError(cleanMessage(t), t);
            }
        }, false);
    }

    private void ensureNeoForge(String nick, boolean launchAfter) {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                String neoId = findInstalledNeoForgeId();
                if (neoId != null) {
                    listener.onStatus("NeoForge 21.1.200 instalado.", 52);
                    finishSetup(nick, neoId, launchAfter);
                    return;
                }

                listener.onStatus("Baixando NeoForge 21.1.200...", 40);
                new NeoForgeDownloadTask(new ModloaderDownloadListener() {
                    @Override
                    public void onDownloadFinished(File downloadedFile) {
                        listener.onStatus("Instalando NeoForge 21.1.200...", 48);
                        listener.onNeoForgeInstallerReady(downloadedFile);
                    }

                    @Override
                    public void onDataNotAvailable() {
                        listener.onError("NeoForge 21.1.200 não foi encontrado no repositório oficial.", null);
                    }

                    @Override
                    public void onDownloadError(Exception e) {
                        listener.onError("Falha ao baixar NeoForge: " + cleanMessage(e), e);
                    }
                }, AscensionConfig.NEOFORGE_VERSION).run();
            } catch (Throwable t) {
                listener.onError(cleanMessage(t), t);
            }
        });
    }

    public void resumeAfterNeoForgeInstaller(String nick, boolean launchAfter) {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                String neoId = findInstalledNeoForgeId();
                if (neoId == null) {
                    throw new IllegalStateException("o instalador terminou, mas a versão NeoForge 21.1.200 não apareceu");
                }
                finishSetup(nick, neoId, launchAfter);
            } catch (Throwable t) {
                listener.onError("Falha após instalar NeoForge: " + cleanMessage(t), t);
            }
        });
    }

    private void finishSetup(String nick, String neoId, boolean launchAfter) throws Exception {
        listener.onStatus("Configurando perfil Ascension Pixelmon...", 57);
        File gameDir = ensureLauncherProfile(neoId);
        ensureLocalAccount(nick, neoId);

        AscensionUpdater updater = new AscensionUpdater(activity, listener::onStatus);
        updater.sync(gameDir);
        listener.onReady(neoId, launchAfter);
    }

    private void ensureLocalAccount(String nick, String selectedVersion) throws Exception {
        File accounts = new File(Tools.DIR_ACCOUNT_NEW);
        if (!accounts.exists() && !accounts.mkdirs()) {
            throw new IllegalStateException("não foi possível criar a pasta de contas locais");
        }

        MinecraftAccount account = MinecraftAccount.load(nick);
        if (account == null) account = new MinecraftAccount();
        account.username = nick;
        account.accessToken = "0";
        account.clientToken = "0";
        account.isMicrosoft = false;
        account.msaRefreshToken = "0";
        account.profileId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nick).getBytes(StandardCharsets.UTF_8)).toString();
        if (selectedVersion != null) account.selectedVersion = selectedVersion;
        account.save();
        PojavProfile.setCurrentProfile(activity, nick);
        prefs.edit().putString("nick", nick).apply();
    }

    private File ensureLauncherProfile(String neoId) {
        LauncherProfiles.load();
        String savedKey = prefs.getString("launcher_profile_key", "");
        MinecraftProfile profile = savedKey.isEmpty() ? null : LauncherProfiles.mainProfileJson.profiles.get(savedKey);

        if (profile == null) {
            for (Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
                MinecraftProfile candidate = entry.getValue();
                if (candidate != null && AscensionConfig.PROFILE_NAME.equals(candidate.name)) {
                    savedKey = entry.getKey();
                    profile = candidate;
                    break;
                }
            }
        }

        if (profile == null) {
            profile = MinecraftProfile.createTemplate();
            savedKey = LauncherProfiles.getFreeProfileKey();
        }

        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date());
        profile.name = AscensionConfig.PROFILE_NAME;
        profile.type = "custom";
        profile.lastVersionId = neoId;
        profile.gameDir = AscensionConfig.GAME_DIR_NAME;
        if (profile.created == null) profile.created = now;
        profile.lastUsed = now;

        LauncherProfiles.mainProfileJson.profiles.put(savedKey, profile);
        LauncherProfiles.write();
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, savedKey)
                .apply();
        prefs.edit().putString("launcher_profile_key", savedKey).apply();

        File gameDir = new File(Tools.DIR_GAME_HOME, AscensionConfig.GAME_DIR_NAME);
        if (!gameDir.exists()) gameDir.mkdirs();
        return gameDir;
    }

    public static boolean isMinecraftInstalled() {
        File dir = new File(Tools.DIR_HOME_VERSION, AscensionConfig.MC_VERSION);
        File json = new File(dir, AscensionConfig.MC_VERSION + ".json");
        File jar = new File(dir, AscensionConfig.MC_VERSION + ".jar");
        return json.isFile() && json.length() > 0 && jar.isFile() && jar.length() > 0;
    }

    public static String findInstalledNeoForgeId() {
        File root = new File(Tools.DIR_HOME_VERSION);
        File[] versions = root.listFiles();
        if (versions == null) return null;
        String target = AscensionConfig.NEOFORGE_VERSION.toLowerCase(Locale.ROOT);
        for (File dir : versions) {
            if (!dir.isDirectory()) continue;
            File json = new File(dir, dir.getName() + ".json");
            if (!json.isFile() || json.length() == 0) continue;
            try {
                String raw = Tools.read(json.getAbsolutePath());
                String lower = raw.toLowerCase(Locale.ROOT);
                if (!lower.contains("neoforge") || !lower.contains(target)) continue;
                JMinecraftVersionList.Version info = Tools.GLOBAL_GSON.fromJson(raw, JMinecraftVersionList.Version.class);
                if (info != null && info.inheritsFrom != null && !AscensionConfig.MC_VERSION.equals(info.inheritsFrom)) continue;
                if (info != null && info.id != null && !info.id.trim().isEmpty()) return info.id;
                return dir.getName();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public File getGameDirIfConfigured() {
        return new File(Tools.DIR_GAME_HOME, AscensionConfig.GAME_DIR_NAME);
    }

    private static String cleanMessage(Throwable t) {
        if (t == null) return "erro desconhecido";
        String m = t.getMessage();
        return (m == null || m.trim().isEmpty()) ? t.getClass().getSimpleName() : m;
    }
}
