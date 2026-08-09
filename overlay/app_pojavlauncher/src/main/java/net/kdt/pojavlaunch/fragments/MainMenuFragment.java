package net.kdt.pojavlaunch.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.JavaGUILauncherActivity;
import net.kdt.pojavlaunch.MainActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.ascension.AscensionBootstrap;
import net.kdt.pojavlaunch.ascension.AscensionConfig;
import net.kdt.pojavlaunch.ascension.AscensionUpdater;

import org.json.JSONObject;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";
    private WebView webView;
    private SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean busy;
    private boolean pendingLaunchAfterInstaller;
    private String pendingNick;
    private AscensionBootstrap bootstrap;

    private final ActivityResultLauncher<Intent> neoForgeInstallerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                busy = false;
                if (bootstrap == null) bootstrap = createBootstrap();
                if (result.getResultCode() != Activity.RESULT_OK) {
                    sendEvent("error", "A instalação do NeoForge não foi concluída.", -1);
                    sendState();
                    return;
                }
                busy = true;
                sendEvent("progress", "Finalizando instalação do NeoForge...", 52);
                bootstrap.resumeAfterNeoForgeInstaller(pendingNick, pendingLaunchAfterInstaller);
            });

    public MainMenuFragment() {
        super(R.layout.fragment_ascension_launcher);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = requireContext().getSharedPreferences(AscensionConfig.PREFS, Activity.MODE_PRIVATE);
        webView = view.findViewById(R.id.ascension_webview);
        if (webView == null) {
            throw new IllegalStateException("Ascension WebView ausente no layout fragment_ascension_launcher");
        }
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.setBackgroundColor(0xFF090A0E);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                sendState();
                checkServerAsync();
            }
        });
        webView.addJavascriptInterface(new AscensionBridge(), "AscensionAndroid");
        webView.loadUrl("file:///android_asset/ui/index.html");
    }

    @Override
    public void onResume() {
        super.onResume();
        sendState();
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            webView.removeJavascriptInterface("AscensionAndroid");
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }

    private final class AscensionBridge {
        @JavascriptInterface
        public String getState() {
            try {
                JSONObject o = new JSONObject();
                String nick = prefs == null ? "" : prefs.getString("nick", "");
                String neoId = AscensionBootstrap.findInstalledNeoForgeId();
                File gameDir = new File(Tools.DIR_GAME_HOME, AscensionConfig.GAME_DIR_NAME);
                File mods = new File(gameDir, "mods");
                o.put("nick", nick);
                o.put("engineReady", true);
                o.put("minecraftInstalled", AscensionBootstrap.isMinecraftInstalled());
                o.put("neoforgeInstalled", neoId != null);
                o.put("neoforgeId", neoId == null ? "" : neoId);
                o.put("prepared", mods.isDirectory() && containsJar(mods));
                o.put("busy", busy);
                o.put("modsDigest", prefs == null ? "" : prefs.getString("mods_digest", ""));
                o.put("minecraft", AscensionConfig.MC_VERSION);
                o.put("neoforge", AscensionConfig.NEOFORGE_VERSION);
                o.put("server", AscensionConfig.SERVER_HOST);
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void saveNick(String nick) {
            String value = nick == null ? "" : nick.trim();
            if (!value.matches("^[A-Za-z0-9_]{3,16}$")) {
                sendEvent("error", "Use um Nick de 3 a 16 caracteres: letras, números ou _.", -1);
                return;
            }
            prefs.edit().putString("nick", value).apply();
            sendEvent("nick", value, -1);
            sendState();
        }

        @JavascriptInterface
        public void prepare() {
            begin(false);
        }

        @JavascriptInterface
        public void play() {
            begin(true);
        }

        @JavascriptInterface
        public void repair() {
            new AscensionUpdater(requireContext(), null).forceRepairNextRun();
            sendEvent("info", "Reparo preparado. O próximo JOGAR baixará mods.zip novamente com rollback seguro.", -1);
            sendState();
        }

        @JavascriptInterface
        public void openWebsite() {
            openUrl(AscensionConfig.WEBSITE);
        }

        @JavascriptInterface
        public void openDiscord() {
            openUrl(AscensionConfig.DISCORD);
        }

        @JavascriptInterface
        public void checkServer() {
            checkServerAsync();
        }
    }

    private void begin(boolean launchAfter) {
        String nick = prefs.getString("nick", "");
        if (!nick.matches("^[A-Za-z0-9_]{3,16}$")) {
            sendEvent("needNick", "Escolha seu Nick antes de jogar.", -1);
            return;
        }
        synchronized (this) {
            if (busy) {
                sendEvent("info", "Uma instalação/atualização já está em andamento.", -1);
                return;
            }
            busy = true;
        }
        pendingNick = nick;
        pendingLaunchAfterInstaller = launchAfter;
        bootstrap = createBootstrap();
        bootstrap.start(nick, launchAfter);
        sendState();
    }

    private AscensionBootstrap createBootstrap() {
        return new AscensionBootstrap(requireActivity(), new AscensionBootstrap.Listener() {
            @Override
            public void onStatus(String message, int percent) {
                sendEvent("progress", message, percent);
            }

            @Override
            public void onNeoForgeInstallerReady(File installer) {
                main.post(() -> {
                    Intent intent = new Intent(requireContext(), JavaGUILauncherActivity.class);
                    intent.putExtra("javaArgs", "-jar " + installer.getAbsolutePath() + " --install-client");
                    intent.putExtra("openLogOutput", false);
                    intent.putExtra("ascension_return_after_vm", true);
                    neoForgeInstallerLauncher.launch(intent);
                });
            }

            @Override
            public void onReady(String neoForgeVersionId, boolean launchAfter) {
                busy = false;
                sendEvent("done", "Ascension Pixelmon pronto.", 100);
                sendState();
                if (launchAfter) launchGame(neoForgeVersionId);
            }

            @Override
            public void onError(String message, Throwable error) {
                busy = false;
                sendEvent("error", message, -1);
                sendState();
            }
        });
    }

    private void launchGame(String versionId) {
        main.post(() -> {
            try {
                Intent mainIntent = new Intent(requireContext(), MainActivity.class);
                mainIntent.putExtra(MainActivity.INTENT_MINECRAFT_VERSION, versionId);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(mainIntent);
                requireActivity().finish();
                android.os.Process.killProcess(android.os.Process.myPid());
            } catch (Throwable t) {
                sendEvent("error", "Falha ao abrir o Minecraft: " + cleanMessage(t), -1);
            }
        });
    }

    private void checkServerAsync() {
        PojavApplication.sExecutorService.execute(() -> {
            boolean online = false;
            long ms = -1;
            long start = System.currentTimeMillis();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(AscensionConfig.SERVER_HOST, AscensionConfig.SERVER_PORT), 3500);
                online = true;
                ms = System.currentTimeMillis() - start;
            } catch (Exception ignored) {
            }
            try {
                JSONObject o = new JSONObject();
                o.put("online", online);
                o.put("ping", ms);
                sendJs("window.AscensionMobile && window.AscensionMobile.onServer(" + JSONObject.quote(o.toString()) + ")");
            } catch (Exception ignored) {
            }
        });
    }

    private void sendState() {
        if (webView == null || prefs == null) return;
        String state = new AscensionBridge().getState();
        sendJs("window.AscensionMobile && window.AscensionMobile.onState(" + JSONObject.quote(state) + ")");
    }

    private void sendEvent(String type, String message, int progress) {
        try {
            JSONObject o = new JSONObject();
            o.put("type", type);
            o.put("message", message == null ? "" : message);
            if (progress >= 0) o.put("progress", progress);
            sendJs("window.AscensionMobile && window.AscensionMobile.onEvent(" + JSONObject.quote(o.toString()) + ")");
        } catch (Exception ignored) {
        }
    }

    private void sendJs(String script) {
        main.post(() -> {
            if (webView != null) webView.evaluateJavascript(script, null);
        });
    }

    private void openUrl(String url) {
        main.post(() -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                sendEvent("error", "Não foi possível abrir o link.", -1);
            }
        });
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

    private String cleanMessage(Throwable t) {
        String m = t == null ? null : t.getMessage();
        return (m == null || m.trim().isEmpty()) ? (t == null ? "erro desconhecido" : t.getClass().getSimpleName()) : m;
    }
}
