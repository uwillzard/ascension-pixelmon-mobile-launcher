package net.kdt.pojavlaunch.ascension;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class AscensionUpdater {
    public interface Listener {
        void onProgress(String message, int percent);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final Listener listener;

    public AscensionUpdater(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(AscensionConfig.PREFS, Context.MODE_PRIVATE);
        this.listener = listener;
    }

    public void sync(File gameDir) throws Exception {
        if (!gameDir.exists() && !gameDir.mkdirs()) {
            throw new IOException("Não foi possível criar a pasta do Ascension.");
        }

        progress("Verificando mods do Ascension...", 66);
        String remoteDigest = fetchModsDigest();
        String installedDigest = prefs.getString("mods_digest", "");
        File mods = new File(gameDir, "mods");
        boolean force = prefs.getBoolean("force_mods_repair", false);
        boolean localMissing = !mods.isDirectory() || !containsJar(mods) || installedDigest.isEmpty();
        boolean needMods = force || localMissing
                || (!remoteDigest.isEmpty() && !installedDigest.equalsIgnoreCase(remoteDigest));

        if (needMods) {
            installModsAtomically(gameDir, remoteDigest);
            prefs.edit().putBoolean("force_mods_repair", false).apply();
        } else if (remoteDigest.isEmpty()) {
            // Fail safe: if GitHub's release API is temporarily unavailable, keep the last
            // validated mods instead of downloading/replacing them blindly.
            progress("Não foi possível consultar o hash remoto; mantendo os mods validados.", 80);
        } else {
            progress("Mods já estão atualizados.", 80);
        }

        installClientFilesFirstTime(gameDir);
        ensureBundledCleanMenu(gameDir);
        ensureServersDat(gameDir);
        prefs.edit().putLong("last_prepared", System.currentTimeMillis()).apply();
        progress("Ascension Pixelmon pronto.", 100);
    }

    public void forceRepairNextRun() {
        prefs.edit().putBoolean("force_mods_repair", true).remove("mods_digest").apply();
    }

    public String getSavedDigest() {
        return prefs.getString("mods_digest", "");
    }

    private void installModsAtomically(File root, String expectedDigest) throws Exception {
        File tempZip = new File(root, "mods.download.tmp");
        File stage = new File(root, "mods.stage");
        File old = new File(root, "mods.old");
        File normalized = new File(root, "mods.normalized");
        File current = new File(root, "mods");
        deleteRecursively(stage);
        deleteRecursively(old);
        deleteRecursively(normalized);
        if (tempZip.exists()) tempZip.delete();

        downloadFile(AscensionConfig.MODS_URL, tempZip, "Baixando mods.zip", 68, 78);
        String actual = "sha256:" + sha256(tempZip);
        if (expectedDigest != null && !expectedDigest.isEmpty()
                && !expectedDigest.equalsIgnoreCase(actual)) {
            tempZip.delete();
            throw new IOException("o SHA-256 de mods.zip não confere");
        }

        progress("Validando e extraindo mods...", 79);
        unzipSafely(tempZip, stage);
        File extracted = normalizeExtractedFolder(stage, "mods");
        if (!containsJar(extracted)) {
            deleteRecursively(stage);
            tempZip.delete();
            throw new IOException("mods.zip não contém arquivos .jar");
        }

        File incoming = extracted;
        if (!incoming.equals(stage)) {
            if (!incoming.renameTo(normalized)) copyDirectory(incoming, normalized);
            deleteRecursively(stage);
            incoming = normalized;
        }

        if (current.exists() && !current.renameTo(old)) {
            deleteRecursively(incoming);
            tempZip.delete();
            throw new IOException("não foi possível preservar a pasta mods atual");
        }

        boolean committed = false;
        try {
            if (!incoming.renameTo(current)) {
                copyDirectory(incoming, current);
                deleteRecursively(incoming);
            }
            committed = true;
        } finally {
            if (!committed) {
                deleteRecursively(current);
                if (old.exists()) old.renameTo(current);
            }
        }

        deleteRecursively(old);
        tempZip.delete();
        prefs.edit().putString("mods_digest", actual).apply();
        progress("Mods atualizados com segurança.", 82);
    }

    private void installClientFilesFirstTime(File root) throws Exception {
        File config = new File(root, "config");
        File options = new File(root, "options.txt");
        if (prefs.getBoolean("client_files_installed", false)) {
            progress("Config e options preservados.", 91);
            return;
        }

        // If this game directory survived an app reinstall, do not overwrite the player's settings.
        if (config.isDirectory() && options.isFile()) {
            prefs.edit().putBoolean("client_files_installed", true).apply();
            progress("Config e options existentes foram preservados.", 91);
            return;
        }

        File cfgZip = new File(root, "config.download.tmp");
        File cfgStage = new File(root, "config.stage");
        File cfgNormalized = new File(root, "config.normalized");
        File optTmp = new File(root, "options.download.tmp");
        deleteRecursively(cfgStage);
        deleteRecursively(cfgNormalized);
        if (cfgZip.exists()) cfgZip.delete();
        if (optTmp.exists()) optTmp.delete();

        downloadFile(AscensionConfig.CONFIG_URL, cfgZip, "Baixando configuração inicial", 83, 87);
        unzipSafely(cfgZip, cfgStage);
        File extracted = normalizeExtractedFolder(cfgStage, "config");
        File incoming = extracted;
        if (!incoming.equals(cfgStage)) {
            if (!incoming.renameTo(cfgNormalized)) copyDirectory(incoming, cfgNormalized);
            deleteRecursively(cfgStage);
            incoming = cfgNormalized;
        }

        downloadFile(AscensionConfig.OPTIONS_URL, optTmp, "Baixando options.txt inicial", 88, 91);

        File oldConfig = new File(root, "config.old");
        deleteRecursively(oldConfig);
        if (config.exists() && !config.renameTo(oldConfig)) {
            throw new IOException("não foi possível preservar config atual");
        }

        boolean committed = false;
        try {
            if (!incoming.renameTo(config)) {
                copyDirectory(incoming, config);
                deleteRecursively(incoming);
            }
            replaceFile(optTmp, options);
            committed = true;
        } finally {
            if (!committed) {
                deleteRecursively(config);
                if (oldConfig.exists()) oldConfig.renameTo(config);
            }
        }

        deleteRecursively(oldConfig);
        cfgZip.delete();
        prefs.edit().putBoolean("client_files_installed", true).apply();
    }

    private void ensureBundledCleanMenu(File root) throws IOException {
        File mods = new File(root, "mods");
        if (!mods.exists() && !mods.mkdirs()) throw new IOException("pasta mods indisponível");
        File target = new File(mods, "Ascension-CleanMenu-1.0.0.jar");
        if (target.exists() && target.length() > 0) return;
        try (InputStream in = context.getAssets().open("bootstrap/Ascension-CleanMenu-1.0.0.jar");
             OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            copy(in, out);
        }
    }

    private void ensureServersDat(File root) throws IOException {
        File servers = new File(root, "servers.dat");
        if (servers.isFile() && servers.length() > 0) return;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(servers))) {
            out.write(createServersDat());
        }
    }

    private String fetchModsDigest() {
        HttpURLConnection c = null;
        try {
            c = openConnection(AscensionConfig.RELEASE_API);
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            if (c.getResponseCode() / 100 != 2) return "";
            String json;
            try (InputStream in = c.getInputStream()) {
                json = new String(readAll(in), StandardCharsets.UTF_8);
            }
            JSONObject root = new JSONObject(json);
            JSONArray assets = root.optJSONArray("assets");
            if (assets == null) return "";
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a != null && "mods.zip".equalsIgnoreCase(a.optString("name"))) {
                    String digest = a.optString("digest", "");
                    if (digest.startsWith("sha256:")) return digest.toLowerCase(Locale.ROOT);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.disconnect();
        }
        return "";
    }

    private void downloadFile(String url, File outFile, String label, int p0, int p1) throws IOException {
        HttpURLConnection c = openConnection(url);
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code / 100 != 2) {
            c.disconnect();
            throw new IOException("download retornou HTTP " + code);
        }
        long total = c.getContentLengthLong();
        long done = 0;
        byte[] buf = new byte[128 * 1024];
        try (InputStream in = new BufferedInputStream(c.getInputStream());
             OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
            int n;
            int last = -1;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    int local = (int) Math.min(100, done * 100 / total);
                    int mapped = p0 + (int) ((p1 - p0) * (local / 100.0));
                    if (mapped != last) {
                        last = mapped;
                        progress(label + " · " + local + "%", mapped);
                    }
                }
            }
        } finally {
            c.disconnect();
        }
        if (outFile.length() == 0) throw new IOException("arquivo baixado vazio");
    }

    private HttpURLConnection openConnection(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "Ascension-Pixelmon-Mobile/1.0 Android");
        c.setRequestProperty("Accept", "application/vnd.github+json, */*");
        return c;
    }

    private byte[] createServersDat() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(9);
            out.writeUTF("servers");
            out.writeByte(10);
            out.writeInt(1);
            writeNbtString(out, "name", "Ascension Pixelmon");
            writeNbtString(out, "ip", AscensionConfig.SERVER_HOST);
            out.writeByte(1);
            out.writeUTF("hidden");
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private void writeNbtString(DataOutputStream out, String name, String value) throws IOException {
        out.writeByte(8);
        out.writeUTF(name);
        out.writeUTF(value);
    }

    private void unzipSafely(File zipFile, File dest) throws IOException {
        if (!dest.exists() && !dest.mkdirs()) throw new IOException("não foi possível criar pasta temporária");
        String canonicalDest = dest.getCanonicalPath() + File.separator;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buffer = new byte[128 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                File out = new File(dest, entry.getName());
                String canonicalOut = out.getCanonicalPath();
                if (!canonicalOut.startsWith(canonicalDest)) throw new IOException("ZIP inválido");
                if (entry.isDirectory()) {
                    if (!out.exists() && !out.mkdirs()) throw new IOException("falha ao criar diretório");
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("falha ao criar diretório");
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        int n;
                        while ((n = zip.read(buffer)) != -1) os.write(buffer, 0, n);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private File normalizeExtractedFolder(File stage, String expected) {
        File nested = new File(stage, expected);
        if (nested.isDirectory()) return nested;
        File[] items = stage.listFiles();
        if (items != null && items.length == 1 && items[0].isDirectory()) {
            File inside = new File(items[0], expected);
            if (inside.isDirectory()) return inside;
        }
        return stage;
    }

    private boolean containsJar(File dir) {
        if (dir == null || !dir.exists()) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) return true;
            if (f.isDirectory() && containsJar(f)) return true;
        }
        return false;
    }

    private void copyDirectory(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) throw new IOException("falha ao criar diretório");
            File[] files = src.listFiles();
            if (files != null) for (File f : files) copyDirectory(f, new File(dst, f.getName()));
        } else {
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("falha ao criar diretório");
            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
                copy(in, out);
            }
        }
    }

    private void replaceFile(File src, File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("diretório indisponível");
        File backup = new File(dst.getAbsolutePath() + ".old");
        if (backup.exists()) backup.delete();
        if (dst.exists() && !dst.renameTo(backup)) throw new IOException("não foi possível preservar arquivo atual");
        boolean ok = false;
        try {
            if (!src.renameTo(dst)) {
                try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
                    copy(in, out);
                }
                src.delete();
            }
            ok = true;
        } finally {
            if (!ok) {
                dst.delete();
                if (backup.exists()) backup.renameTo(dst);
            } else {
                backup.delete();
            }
        }
    }

    private String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[128 * 1024];
        try (InputStream in = new FileInputStream(file)) {
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return sb.toString();
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) for (File f : files) deleteRecursively(f);
        }
        file.delete();
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[128 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    }

    private byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(in, out);
        return out.toByteArray();
    }

    private void progress(String message, int percent) {
        if (listener != null) listener.onProgress(message, percent);
    }
}
