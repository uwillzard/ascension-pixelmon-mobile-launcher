package net.kdt.pojavlaunch.ascension;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class AscensionModpackUpdater {
    private AscensionModpackUpdater() {}

    public interface Listener {
        void onReady();
        void onError(String message);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final String RELEASE_API =
            "https://api.github.com/repos/uwillzard/ascension-pixelmon-modpack/releases/tags/v1.0.0";
    private static final String PREFERRED_ASSET = "mods-mobile.zip";
    private static final String FALLBACK_ASSET = "mods.zip";

    private static final String FALLBACK_MODS_URL =
            "https://github.com/uwillzard/ascension-pixelmon-modpack/releases/download/v1.0.0/mods.zip";
    private static final String CONFIG_URL =
            "https://github.com/uwillzard/ascension-pixelmon-modpack/releases/download/v1.0.0/config.zip";
    private static final String OPTIONS_URL =
            "https://github.com/uwillzard/ascension-pixelmon-modpack/releases/download/v1.0.0/options.txt";

    public static void updateBeforeLaunch(Context context, File gameDir, Listener listener) {
        Toast.makeText(context, "Ascension: verificando arquivos...", Toast.LENGTH_SHORT).show();

        EXECUTOR.execute(() -> {
            try {
                if (gameDir == null) {
                    throw new IOException("Pasta do perfil Minecraft nao encontrada.");
                }
                if (!gameDir.exists() && !gameDir.mkdirs()) {
                    throw new IOException("Nao foi possivel criar a pasta do jogo.");
                }

                File stateDir = new File(gameDir, ".ascension-mobile");
                if (!stateDir.exists() && !stateDir.mkdirs()) {
                    throw new IOException("Nao foi possivel criar a pasta de estado Ascension.");
                }

                recoverInterruptedUpdate(gameDir);
                installFirstRunFiles(gameDir, stateDir);
                updateMods(gameDir, stateDir);

                MAIN.post(() -> {
                    Toast.makeText(context, "Ascension: arquivos prontos.", Toast.LENGTH_SHORT).show();
                    listener.onReady();
                });
            } catch (Exception e) {
                e.printStackTrace();
                String raw = e.getMessage();
                final String msg = raw == null || raw.trim().isEmpty()
                        ? e.getClass().getSimpleName()
                        : raw;
                MAIN.post(() -> listener.onError(msg));
            }
        });
    }

    private static void recoverInterruptedUpdate(File gameDir) throws IOException {
        File mods = new File(gameDir, "mods");
        File backup = new File(gameDir, "mods.ascension-backup");

        if (!mods.exists() && backup.exists()) {
            if (!backup.renameTo(mods)) {
                copyDirectory(backup, mods);
                deleteRecursively(backup);
            }
        } else if (mods.exists() && backup.exists()) {
            deleteRecursively(backup);
        }
    }

    private static void installFirstRunFiles(File gameDir, File stateDir) throws Exception {
        File marker = new File(stateDir, "first-install.ok");
        if (marker.exists()) {
            return;
        }

        File temp = new File(stateDir, "first-install-temp");
        deleteRecursively(temp);
        if (!temp.mkdirs()) {
            throw new IOException("Falha ao preparar primeira instalacao.");
        }

        try {
            File configZip = new File(temp, "config.zip");
            download(CONFIG_URL, configZip);
            validateZip(configZip);

            File configStage = new File(temp, "config-stage");
            extractZip(configZip, configStage);

            File possibleConfigFolder = new File(configStage, "config");
            if (possibleConfigFolder.isDirectory()) {
                mergeDirectory(possibleConfigFolder, new File(gameDir, "config"));
            } else {
                mergeDirectory(configStage, new File(gameDir, "config"));
            }

            File optionsTmp = new File(temp, "options.txt");
            download(OPTIONS_URL, optionsTmp);
            if (!optionsTmp.isFile() || optionsTmp.length() == 0) {
                throw new IOException("options.txt baixado esta vazio.");
            }
            copyFile(optionsTmp, new File(gameDir, "options.txt"));

            writeText(marker, "ok\n");
        } finally {
            deleteRecursively(temp);
        }
    }

    private static void updateMods(File gameDir, File stateDir) throws Exception {
        RemoteAsset remote = resolveModsAsset();

        File modsDir = new File(gameDir, "mods");
        File fingerprintFile = new File(stateDir, "mods.remote");
        String installedFingerprint = readText(fingerprintFile).trim();

        if (modsDir.isDirectory()
                && !remote.fingerprint.isEmpty()
                && remote.fingerprint.equals(installedFingerprint)) {
            return;
        }

        File tempRoot = new File(stateDir, "update-temp");
        deleteRecursively(tempRoot);
        if (!tempRoot.mkdirs()) {
            throw new IOException("Falha ao criar pasta temporaria.");
        }

        File backup = new File(gameDir, "mods.ascension-backup");
        boolean hadOldMods = modsDir.exists();
        boolean oldModsMoved = false;

        try {
            File zip = new File(tempRoot, "mods.zip.part");
            download(remote.url, zip);
            validateZip(zip);

            if (remote.expectedSha256 != null && !remote.expectedSha256.isEmpty()) {
                String actualSha = sha256(zip);
                if (!actualSha.equalsIgnoreCase(remote.expectedSha256)) {
                    throw new IOException("SHA-256 do pacote de mods nao confere.");
                }
            }

            File stageRoot = new File(tempRoot, "stage");
            extractZip(zip, stageRoot);

            File preparedMods = new File(stageRoot, "mods");
            if (!preparedMods.isDirectory()) {
                preparedMods = stageRoot;
            }

            File[] stagedFiles = preparedMods.listFiles();
            if (stagedFiles == null || stagedFiles.length == 0) {
                throw new IOException("O ZIP de mods foi extraido vazio.");
            }

            deleteRecursively(backup);

            if (modsDir.exists()) {
                if (!modsDir.renameTo(backup)) {
                    throw new IOException("Nao foi possivel preservar a pasta mods antiga.");
                }
                oldModsMoved = true;
            }

            if (!preparedMods.renameTo(modsDir)) {
                copyDirectory(preparedMods, modsDir);
            }

            File[] installed = modsDir.listFiles();
            if (installed == null || installed.length == 0) {
                throw new IOException("A nova pasta mods ficou vazia.");
            }

            String fingerprint = remote.fingerprint;
            if (fingerprint == null || fingerprint.isEmpty()) {
                fingerprint = "sha256:" + sha256(zip);
            }
            writeText(fingerprintFile, fingerprint + "\n");

            deleteRecursively(backup);
        } catch (Exception installError) {
            if (oldModsMoved) {
                deleteRecursively(modsDir);
                if (backup.exists()) {
                    if (!backup.renameTo(modsDir)) {
                        copyDirectory(backup, modsDir);
                        deleteRecursively(backup);
                    }
                }
            } else if (!hadOldMods) {
                deleteRecursively(modsDir);
            }
            throw installError;
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private static RemoteAsset resolveModsAsset() {
        try {
            String json = readUrlAsString(RELEASE_API);
            JsonObject release = JsonParser.parseString(json).getAsJsonObject();
            JsonArray assets = release.getAsJsonArray("assets");

            JsonObject preferred = null;
            JsonObject fallback = null;

            if (assets != null) {
                for (JsonElement element : assets) {
                    JsonObject asset = element.getAsJsonObject();
                    String name = string(asset, "name");
                    if (PREFERRED_ASSET.equalsIgnoreCase(name)) {
                        preferred = asset;
                    }
                    if (FALLBACK_ASSET.equalsIgnoreCase(name)) {
                        fallback = asset;
                    }
                }
            }

            JsonObject selected = preferred != null ? preferred : fallback;
            if (selected != null) {
                String url = string(selected, "browser_download_url");
                String digest = string(selected, "digest");
                String expectedSha = normalizeSha256(digest);

                String fingerprint;
                if (digest != null && !digest.isEmpty()) {
                    fingerprint = digest;
                } else {
                    String updatedAt = safe(string(selected, "updated_at"));
                    String size = safe(string(selected, "size"));
                    long assetId = longValue(selected, "id");
                    fingerprint = "github:" + assetId + ":" + updatedAt + ":" + size;
                }

                if (url != null && !url.isEmpty()) {
                    return new RemoteAsset(url, fingerprint, expectedSha);
                }
            }
        } catch (Exception ignored) {
            // A release API e apenas a primeira estrategia.
        }

        try {
            return headFingerprint(FALLBACK_MODS_URL);
        } catch (Exception ignored) {
            return new RemoteAsset(FALLBACK_MODS_URL, "", null);
        }
    }

    private static RemoteAsset headFingerprint(String url) throws Exception {
        HttpURLConnection conn = open(url, "HEAD");
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) {
                throw new IOException("HEAD falhou: HTTP " + code);
            }

            String etag = safe(conn.getHeaderField("ETag"));
            String lastModified = safe(conn.getHeaderField("Last-Modified"));
            String length = safe(conn.getHeaderField("Content-Length"));
            String fp = "head:" + etag + ":" + lastModified + ":" + length;

            if (etag.isEmpty() && lastModified.isEmpty() && length.isEmpty()) {
                fp = "";
            }
            return new RemoteAsset(url, fp, null);
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection open(String original, String method) throws Exception {
        URL url = new URL(original);
        String currentMethod = method;

        for (int i = 0; i < 8; i++) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod(currentMethod);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(120000);
            conn.setRequestProperty("User-Agent", "AscensionPixelmonMobile/1.0");
            conn.setRequestProperty("Accept", "*/*");

            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.trim().isEmpty()) {
                    throw new IOException("Redirect sem Location.");
                }
                url = new URL(url, location);
                if (code == 303) {
                    currentMethod = "GET";
                }
                continue;
            }

            return conn;
        }

        throw new IOException("Muitos redirects durante o download.");
    }

    private static void download(String url, File out) throws Exception {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Falha ao criar pasta de download.");
        }

        HttpURLConnection conn = open(url, "GET");
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Download falhou: HTTP " + code);
            }

            try (InputStream input = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream fos = new FileOutputStream(out);
                 BufferedOutputStream output = new BufferedOutputStream(fos)) {
                byte[] buffer = new byte[128 * 1024];
                int read;
                long total = 0;

                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    total += read;
                }

                output.flush();
                if (total <= 0) {
                    throw new IOException("Download vazio.");
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String readUrlAsString(String url) throws Exception {
        HttpURLConnection conn = open(url, "GET");
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("GitHub API falhou: HTTP " + code);
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static void validateZip(File zipFile) throws IOException {
        if (!zipFile.isFile() || zipFile.length() < 22) {
            throw new IOException("ZIP invalido ou vazio.");
        }

        try (ZipFile zip = new ZipFile(zipFile)) {
            if (!zip.entries().hasMoreElements()) {
                throw new IOException("ZIP sem arquivos.");
            }
        }
    }

    private static void extractZip(File zipFile, File destination) throws IOException {
        deleteRecursively(destination);
        if (!destination.mkdirs()) {
            throw new IOException("Falha ao criar staging.");
        }

        String destCanonical = destination.getCanonicalPath() + File.separator;

        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buffer = new byte[128 * 1024];

            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(destination, entry.getName());
                String outCanonical = out.getCanonicalPath();

                if (!outCanonical.startsWith(destCanonical)) {
                    throw new IOException("Entrada ZIP insegura: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    if (!out.exists() && !out.mkdirs()) {
                        throw new IOException("Falha ao criar diretorio: " + entry.getName());
                    }
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Falha ao criar diretorio de arquivo.");
                    }

                    try (FileOutputStream fos = new FileOutputStream(out);
                         BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, read);
                        }
                    }
                }

                zis.closeEntry();
            }
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private static String normalizeSha256(String digest) {
        if (digest == null) {
            return null;
        }

        String lower = digest.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("sha256:")) {
            return lower.substring("sha256:".length());
        }
        if (lower.matches("[0-9a-f]{64}")) {
            return lower;
        }
        return null;
    }

    private static String string(JsonObject obj, String name) {
        try {
            JsonElement e = obj.get(name);
            return e == null || e.isJsonNull() ? null : e.getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long longValue(JsonObject obj, String name) {
        try {
            JsonElement e = obj.get(name);
            return e == null || e.isJsonNull() ? 0L : e.getAsLong();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String readText(File file) {
        if (!file.isFile()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException ignored) {
        }
        return sb.toString();
    }

    private static void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Falha ao criar pasta de estado.");
        }

        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(text);
        }
    }

    private static void mergeDirectory(File from, File to) throws IOException {
        if (!from.exists()) {
            return;
        }
        if (!to.exists() && !to.mkdirs()) {
            throw new IOException("Falha ao criar destino.");
        }

        File[] children = from.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            File target = new File(to, child.getName());
            if (child.isDirectory()) {
                mergeDirectory(child, target);
            } else {
                copyFile(child, target);
            }
        }
    }

    private static void copyDirectory(File from, File to) throws IOException {
        if (!from.isDirectory()) {
            throw new IOException("Origem nao e diretorio.");
        }
        if (!to.exists() && !to.mkdirs()) {
            throw new IOException("Falha ao criar diretorio.");
        }

        File[] children = from.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            File target = new File(to, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, target);
            } else {
                copyFile(child, target);
            }
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        File parent = to.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Falha ao criar pasta do arquivo.");
        }

        try (InputStream in = new BufferedInputStream(new FileInputStream(from));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(to))) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }

        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static final class RemoteAsset {
        final String url;
        final String fingerprint;
        final String expectedSha256;

        RemoteAsset(String url, String fingerprint, String expectedSha256) {
            this.url = url;
            this.fingerprint = fingerprint == null ? "" : fingerprint;
            this.expectedSha256 = expectedSha256;
        }
    }
}
