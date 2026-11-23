package com.winampytm.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String TARGET_URL = "https://music.youtube.com/";
    private static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final String PREFS_NAME = "winamp_ytm_prefs";
    private static final String KEY_SCRIPT_URL = "saved_script_url";
    private static final String KEY_SCRIPT_SOURCE = "script_source_label";
    private static final String KEY_SCRIPT_UPDATED_AT = "script_updated_at";
    private static final String CUSTOM_SCRIPT_FILE = "custom_userscript.js";

    private static final int REQ_PICK_SCRIPT_FILE = 1001;
    private static final int REQ_WEBVIEW_FILE_CHOOSER = 1002;

    private FrameLayout rootLayout;
    private WebView webView;
    private View customFullscreenView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ValueCallback<Uri[]> webFilePathCallback;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setUserAgentString(DESKTOP_USER_AGENT);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.US) : "";
                // YouTube Music ve Google giris sayfalari uygulama icinde kalsin
                if (host.contains("youtube.com")
                        || host.contains("youtu.be")
                        || host.contains("google.com")
                        || host.contains("gstatic.com")
                        || host.contains("googleapis.com")) {
                    return false;
                }
                // Harici baglantilari varsayilan tarayicida ac
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.startsWith("https://music.youtube.com")) {
                    mainHandler.postDelayed(() -> injectActiveUserScript(), 700);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customFullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customFullscreenView = view;
                customViewCallback = callback;
                rootLayout.addView(customFullscreenView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                enterImmersiveMode();
            }

            @Override
            public void onHideCustomView() {
                if (customFullscreenView == null) {
                    return;
                }
                rootLayout.removeView(customFullscreenView);
                customFullscreenView = null;
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
                enterImmersiveMode();
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (webFilePathCallback != null) {
                    webFilePathCallback.onReceiveValue(null);
                }
                webFilePathCallback = filePathCallback;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    startActivityForResult(intent, REQ_WEBVIEW_FILE_CHOOSER);
                    return true;
                } catch (Exception e) {
                    webFilePathCallback = null;
                    return false;
                }
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) {
                    }
                }
            }
        });

        rootLayout.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Sag ustte yari saydam JS yonetim dugmesi
        addSettingsButton();

        setContentView(rootLayout);
        enterImmersiveMode();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(TARGET_URL);
        }
    }

    private void addSettingsButton() {
        TextView btn = new TextView(this);
        btn.setText("JS");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(11f);
        btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setAlpha(0.32f);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#CC1A1A1A"));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.parseColor("#6600E676"));
        btn.setBackground(bg);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(34), dp(34));
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.topMargin = dp(10);
        lp.rightMargin = dp(10);

        btn.setOnClickListener(v -> showScriptManagerDialog());
        rootLayout.addView(btn, lp);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            //noinspection deprecation
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
        }
    }

    private File getCustomScriptFile() {
        return new File(getFilesDir(), CUSTOM_SCRIPT_FILE);
    }

    private String loadActiveUserScript() {
        File customFile = getCustomScriptFile();
        try {
            if (customFile.exists() && customFile.length() > 0) {
                return readStreamToString(new FileInputStream(customFile));
            }
            return readStreamToString(getAssets().open("userscript.js"));
        } catch (Exception e) {
            return null;
        }
    }

    private String readStreamToString(InputStream is) throws Exception {
        try (InputStream input = is; ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            String text = baos.toString(StandardCharsets.UTF_8.name());
            if (text.startsWith("﻿")) {
                text = text.substring(1);
            }
            return text;
        }
    }

    private void injectActiveUserScript() {
        executor.execute(() -> {
            final String script = loadActiveUserScript();
            if (script == null || script.trim().isEmpty()) {
                return;
            }
            mainHandler.post(() -> {
                if (webView != null) {
                    webView.evaluateJavascript(script, null);
                }
            });
        });
    }

    private void saveCustomScript(String code, String sourceLabel) throws Exception {
        File customFile = getCustomScriptFile();
        try (FileOutputStream fos = new FileOutputStream(customFile)) {
            fos.write(code.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
        String now = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date());
        prefs.edit()
                .putString(KEY_SCRIPT_SOURCE, sourceLabel)
                .putString(KEY_SCRIPT_UPDATED_AT, now)
                .apply();
    }

    private void showScriptManagerDialog() {
        File customFile = getCustomScriptFile();
        boolean hasCustom = customFile.exists() && customFile.length() > 0;
        String statusText;
        if (hasCustom) {
            long kb = Math.max(1, customFile.length() / 1024);
            String src = prefs.getString(KEY_SCRIPT_SOURCE, "Ozel Kod");
            String updated = prefs.getString(KEY_SCRIPT_UPDATED_AT, "-");
            statusText = "Aktif: " + src + " (" + kb + " KB)\nGuncelleme: " + updated;
        } else {
            statusText = "Aktif: Gomulu Varsayilan (Winamp YTM v2.95.37)";
        }

        String[] options = new String[]{
                "Dosyadan JS Yukle (.js / .txt)",
                "URL'den Guncelle (GitHub Raw / Gist vb.)",
                "Panodan Kodu Yapistir",
                "Varsayilan Koda Sifirla",
                "Sayfayi Yenile"
        };

        new AlertDialog.Builder(this)
                .setTitle("JavaScript Yoneticisi")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openScriptFilePicker();
                            break;
                        case 1:
                            showUrlUpdateDialog();
                            break;
                        case 2:
                            pasteScriptFromClipboard();
                            break;
                        case 3:
                            resetToBundledScript();
                            break;
                        case 4:
                            reloadYouTubeMusic();
                            break;
                    }
                })
                .setNeutralButton("Durum", (d, w) ->
                        Toast.makeText(this, statusText, Toast.LENGTH_LONG).show())
                .setNegativeButton("Kapat", (d, w) -> enterImmersiveMode())
                .setOnDismissListener(d -> enterImmersiveMode())
                .show();
    }

    private void openScriptFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/plain", "text/javascript", "application/javascript", "application/octet-stream"
        });
        try {
            startActivityForResult(intent, REQ_PICK_SCRIPT_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "Dosya secici acilamadi.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showUrlUpdateDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(12), dp(20), dp(4));

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://raw.githubusercontent.com/.../script.js");
        input.setText(prefs.getString(KEY_SCRIPT_URL, ""));
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("URL'den JS Guncelle")
                .setMessage("Yeni JavaScript dosyasinin dogrudan (raw) HTTPS baglantisini girin:")
                .setView(container)
                .setPositiveButton("Indir ve Uygula", (d, w) -> {
                    String urlStr = input.getText().toString().trim();
                    if (!urlStr.startsWith("https://")) {
                        Toast.makeText(this, "Lutfen gecerli bir https:// baglantisi girin.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    prefs.edit().putString(KEY_SCRIPT_URL, urlStr).apply();
                    downloadAndApplyScript(urlStr);
                })
                .setNegativeButton("Iptal", (d, w) -> enterImmersiveMode())
                .show();
    }

    private void downloadAndApplyScript(String urlStr) {
        Toast.makeText(this, "Kod indiriliyor...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", DESKTOP_USER_AGENT);
                int status = conn.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP " + status);
                }
                String code = readStreamToString(conn.getInputStream());
                if (code.trim().isEmpty()) {
                    throw new Exception("Bos dosya");
                }
                saveCustomScript(code, "URL");
                mainHandler.post(() -> {
                    Toast.makeText(this, "JS guncellendi, sayfa yenileniyor.", Toast.LENGTH_SHORT).show();
                    reloadYouTubeMusic();
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(this, "Indirme basarisiz: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private void pasteScriptFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "Panoda metin bulunamadi.", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            Toast.makeText(this, "Panoda metin bulunamadi.", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text == null || text.toString().trim().isEmpty()) {
            Toast.makeText(this, "Pano bos.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String code = text.toString();
        executor.execute(() -> {
            try {
                saveCustomScript(code, "Pano");
                mainHandler.post(() -> {
                    Toast.makeText(this, "Panodaki JS kaydedildi, yenileniyor.", Toast.LENGTH_SHORT).show();
                    reloadYouTubeMusic();
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(this, "Kaydetme hatasi: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void resetToBundledScript() {
        File customFile = getCustomScriptFile();
        if (customFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            customFile.delete();
        }
        prefs.edit().remove(KEY_SCRIPT_SOURCE).remove(KEY_SCRIPT_UPDATED_AT).apply();
        Toast.makeText(this, "Varsayilan koda donuldu, yenileniyor.", Toast.LENGTH_SHORT).show();
        reloadYouTubeMusic();
    }

    private void reloadYouTubeMusic() {
        if (webView != null) {
            webView.reload();
        }
        enterImmersiveMode();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_WEBVIEW_FILE_CHOOSER) {
            if (webFilePathCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    if (data.getData() != null) {
                        results = new Uri[]{data.getData()};
                    } else if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    }
                }
                webFilePathCallback.onReceiveValue(results);
                webFilePathCallback = null;
            }
            return;
        }

        if (requestCode == REQ_PICK_SCRIPT_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            final Uri fileUri = data.getData();
            executor.execute(() -> {
                try (InputStream is = getContentResolver().openInputStream(fileUri)) {
                    if (is == null) {
                        throw new Exception("Dosya okunamadi");
                    }
                    String code = readStreamToString(is);
                    if (code.trim().isEmpty()) {
                        throw new Exception("Bos dosya");
                    }
                    saveCustomScript(code, "Yerel Dosya");
                    mainHandler.post(() -> {
                        Toast.makeText(this, "Yeni JS dosyasi yuklendi, yenileniyor.", Toast.LENGTH_SHORT).show();
                        reloadYouTubeMusic();
                    });
                } catch (Exception e) {
                    mainHandler.post(() ->
                            Toast.makeText(this, "Dosya yuklenemedi: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customFullscreenView != null) {
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                }
                return true;
            }
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
