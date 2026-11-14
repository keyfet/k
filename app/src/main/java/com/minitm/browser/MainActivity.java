package com.minitm.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int IMPORT_SCRIPT = 100;
    private static final int CHOOSE_WEB_FILE = 101;
    private static final String PREFS = "user_scripts";
    private static final String PREFS_KEY = "scripts_json_v1";
    private static final String START_URL = "https://www.google.com/";
    private final List<UserScript> scripts = new ArrayList<>();

    private WebView web;
    private EditText urlBox;
    private ProgressBar progress;
    private Button backButton, forwardButton;
    private ScriptHandler documentStartHandler;
    private String combinedScript = "";
    private ValueCallback<Uri[]> webFileChooser;
    private String defaultUserAgent;
    private boolean desktopMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadScripts();
        buildInterface();
        configureWebView();
        refreshInjection();
        String initial = getSharedPreferences(PREFS, MODE_PRIVATE).getString("last_url", START_URL);
        if (initial == null || !initial.startsWith("http")) initial = START_URL;
        web.loadUrl(extractSharedUrl(getIntent(), initial));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Button toolbarButton(String text, LinearLayout row, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(5), 0, dp(5), 0);
        row.addView(button, new LinearLayout.LayoutParams(dp(44), dp(44)));
        button.setOnClickListener(listener);
        return button;
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(2), dp(2), dp(2), dp(2));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(50)));
        backButton = toolbarButton("‹", toolbar, v -> { if (web.canGoBack()) web.goBack(); });
        forwardButton = toolbarButton("›", toolbar, v -> { if (web.canGoForward()) web.goForward(); });
        toolbarButton("↻", toolbar, v -> web.reload());

        urlBox = new EditText(this);
        urlBox.setSingleLine(true);
        urlBox.setTextSize(14);
        urlBox.setSelectAllOnFocus(true);
        urlBox.setHint("Site adresi...");
        urlBox.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlBox.setImeOptions(EditorInfo.IME_ACTION_GO);
        toolbar.addView(urlBox, new LinearLayout.LayoutParams(0, dp(44), 1));
        urlBox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                browse(urlBox.getText().toString());
                return true;
            }
            return false;
        });
        toolbarButton("JS", toolbar, v -> showScriptManager());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));
        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void configureWebView() {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        defaultUserAgent = settings.getUserAgentString();
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (Exception ex) { toast("Bağlantı açılamadı"); }
                return true;
            }
            @Override public void onPageCommitVisible(WebView view, String url) {
                // Eski WebView sürümlerinde belge başı ekleme desteklenmezse yedek yol.
                if (documentStartHandler == null) injectFallback();
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (documentStartHandler == null) injectFallback();
                if (!urlBox.hasFocus()) urlBox.setText(url);
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("last_url", url).apply();
                }
                updateButtons();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress == 100 ? View.GONE : View.VISIBLE);
            }
            @Override public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (webFileChooser != null) webFileChooser.onReceiveValue(null);
                webFileChooser = callback;
                try { startActivityForResult(params.createIntent(), CHOOSE_WEB_FILE); return true; }
                catch (Exception e) {
                    webFileChooser = null;
                    return false;
                }
            }
        });
        web.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setMimeType(mimetype);
                req.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) == null ? "" :
                        CookieManager.getInstance().getCookie(url));
                req.addRequestHeader("User-Agent", userAgent);
                String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                manager.enqueue(req);
                toast("İndirme başladı: " + filename);
            } catch (Exception e) { toast("İndirme yapılamadı: " + e.getMessage()); }
        });
        updateButtons();
    }

    private void injectFallback() {
        web.evaluateJavascript(combinedScript, null);
    }

    private void refreshInjection() {
        if (documentStartHandler != null) {
            documentStartHandler.remove();
            documentStartHandler = null;
        }
        combinedScript = generateCombinedJavascript();
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                documentStartHandler = WebViewCompat.addDocumentStartJavaScript(
                        web, combinedScript, Collections.singleton("*"));
            }
        } catch (Exception e) {
            documentStartHandler = null;
            toast("Belge başı desteği yok; sayfa yüklendikten sonra çalışacak");
        }
    }

    private String generateCombinedJavascript() {
        StringBuilder out = new StringBuilder();
        try (InputStream input = getAssets().open("engine.js")) {
            out.append(new String(readAll(input, 300000), StandardCharsets.UTF_8));
        } catch (Exception e) {
            toast("Betik motoru yüklenemedi");
            return "";
        }
        for (int i = 0; i < scripts.size(); i++) {
            UserScript s = scripts.get(i);
            if (!s.enabled) continue;
            JSONObject meta = s.metadata();
            try {
                // Betikler doğrudan fonksiyon gövdesinde eklenir; eval ve ağdan kod indirme yok.
                out.append("\n;window.__MiniTMEngine.register(")
                    .append(meta.toString())
                    .append(", function(GM_getValue,GM_setValue,GM_deleteValue,GM_listValues,")
                    .append("GM_addStyle,GM_xmlhttpRequest,GM_openInTab,GM_download,")
                    .append("GM_registerMenuCommand,GM_setClipboard,GM_log,GM_info,unsafeWindow,GM){\n")
                    .append(s.source).append("\n});\n");
            } catch (Exception ex) {
                toast("Betik hazırlama hatası: " + s.name());
            }
        }
        return out.toString();
    }

    private static byte[] readAll(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > limit) throw new Exception("Dosya çok büyük (üst sınır " + limit + " bayt)");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void browse(String text) {
        String destination = text.trim();
        if (destination.isEmpty()) return;
        if (!destination.matches("(?i)^https?://.*")) {
            if (destination.matches("(?i)^[^\\s]+\\.[^\\s]+.*")) destination = "https://" + destination;
            else destination = "https://www.google.com/search?q=" +
                    Uri.encode(destination);
        }
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(urlBox.getWindowToken(), 0);
        urlBox.clearFocus();
        web.loadUrl(destination);
    }

    private void updateButtons() {
        if (backButton == null || web == null) return;
        backButton.setEnabled(web.canGoBack());
        forwardButton.setEnabled(web.canGoForward());
    }

    private void showScriptManager() {
        String[] items = new String[scripts.size() + 2];
        for (int i = 0; i < scripts.size(); i++) {
            UserScript s = scripts.get(i);
            items[i] = (s.enabled ? "● " : "○ ") + s.name() + "  ·  " + s.matchSummary();
        }
        items[scripts.size()] = "⚙ Bu sayfanın betik komutları";
        items[scripts.size() + 1] = "▣ Masaüstü sitesi: " + (desktopMode ? "açık" : "kapalı");
        new AlertDialog.Builder(this).setTitle("Betikler (" + scripts.size() + ")")
            .setItems(items, (dialog, which) -> {
                if (which < scripts.size()) showScriptActions(which);
                else if (which == scripts.size()) showPageCommands();
                else toggleDesktopMode();
            })
            .setPositiveButton("Yeni", (d, w) -> editScript(-1))
            .setNeutralButton("İçe aktar", (d, w) -> importScript())
            .setNegativeButton("Kapat", null)
            .show();
    }

    private void toggleDesktopMode() {
        desktopMode = !desktopMode;
        if (desktopMode) {
            String ua = defaultUserAgent.replace("; wv", "")
                    .replace(" Mobile", "").replace("; Mobile", "");
            web.getSettings().setUserAgentString(ua);
        } else web.getSettings().setUserAgentString(defaultUserAgent);
        web.reload();
        toast(desktopMode ? "Masaüstü görünümü" : "Mobil görünüm");
    }

    private void showScriptActions(int index) {
        UserScript s = scripts.get(index);
        String[] actions = {
            s.enabled ? "Devre dışı bırak" : "Etkinleştir", "Kodu düzenle", "Sil"
        };
        new AlertDialog.Builder(this).setTitle(s.name())
            .setItems(actions, (d, which) -> {
                if (which == 0) {
                    s.enabled = !s.enabled;
                    persistAndReload();
                } else if (which == 1) editScript(index);
                else new AlertDialog.Builder(this).setTitle("Betiği sil?")
                        .setMessage(s.name()).setPositiveButton("Sil", (x, y) -> {
                            scripts.remove(index);
                            persistAndReload();
                        }).setNegativeButton("Vazgeç", null).show();
            }).show();
    }

    private void editScript(int index) {
        final boolean isNew = index < 0;
        String initial = isNew ?
            "// ==UserScript==\n// @name         Yeni betik\n// @match        https://example.com/*\n// @grant        none\n// @run-at       document-end\n// ==/UserScript==\n\n(function () {\n  'use strict';\n  console.log('MiniTM çalışıyor');\n})();\n"
            : scripts.get(index).source;
        EditText editor = new EditText(this);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTextSize(12);
        editor.setTypeface(android.graphics.Typeface.MONOSPACE);
        editor.setHorizontallyScrolling(true);
        editor.setText(initial);
        editor.setMinLines(14);
        editor.setPadding(dp(12), dp(8), dp(12), dp(8));
        editor.setSelection(editor.length());
        ScrollView scroll = new ScrollView(this);
        scroll.addView(editor);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(isNew ? "Yeni .user.js betiği" : "Betiği düzenle")
            .setView(scroll)
            .setPositiveButton("Kaydet", null)
            .setNegativeButton("Vazgeç", null)
            .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String source = editor.getText().toString();
            if (source.trim().isEmpty()) { editor.setError("Kod boş olamaz"); return; }
            if (source.length() > 300000) { editor.setError("Betik en fazla 300 KB olabilir"); return; }
            if (isNew) scripts.add(new UserScript(source, true));
            else scripts.get(index).source = source;
            persistAndReload();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void importScript() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(intent, IMPORT_SCRIPT); }
        catch (Exception e) { toast("Dosya seçici açılamadı"); }
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CHOOSE_WEB_FILE) {
            if (webFileChooser != null) {
                webFileChooser.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                webFileChooser = null;
            }
        } else if (requestCode == IMPORT_SCRIPT && resultCode == RESULT_OK &&
                data != null && data.getData() != null) {
            try (InputStream input = getContentResolver().openInputStream(data.getData())) {
                if (input == null) throw new Exception("Dosya okunamadı");
                String source = new String(readAll(input, 300000), StandardCharsets.UTF_8);
                if (source.trim().isEmpty()) throw new Exception("Dosya boş");
                UserScript script = new UserScript(source, true);
                scripts.add(script);
                persistAndReload();
                toast("Yüklendi: " + script.name());
            } catch (Exception e) { toast("İçe aktarma hatası: " + e.getMessage()); }
        }
    }

    private void showPageCommands() {
        web.evaluateJavascript("JSON.stringify(window.__MiniTMCommands ? window.__MiniTMCommands() : [])",
            result -> {
                try {
                    if (result == null || result.equals("null")) { toast("Komut bulunamadı"); return; }
                    JSONArray commands = new JSONArray(new org.json.JSONTokener(result).nextValue().toString());
                    if (commands.length() == 0) { toast("Bu sayfada tanımlı komut yok"); return; }
                    String[] entries = new String[commands.length()];
                    for (int i = 0; i < commands.length(); i++) {
                        entries[i] = commands.getJSONObject(i).optString("name", "Komut " + (i+1));
                    }
                    new AlertDialog.Builder(this).setTitle("Betik komutları").setItems(entries,
                        (dialog, which) -> web.evaluateJavascript(
                                "window.__MiniTMRunCommand(" + which + ")", null)).show();
                } catch (Exception e) { toast("Komut listesi okunamadı"); }
            });
    }

    private void loadScripts() {
        String json = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREFS_KEY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                scripts.add(new UserScript(item.optString("id", UUID.randomUUID().toString()),
                        item.optString("source", ""), item.optBoolean("enabled", true)));
            }
        } catch (JSONException ignored) { toast("Kayıtlı betikler okunamadı"); }
    }

    private void persistAndReload() {
        JSONArray arr = new JSONArray();
        for (UserScript s : scripts) {
            JSONObject item = new JSONObject();
            try { item.put("id", s.id); item.put("source", s.source);
                item.put("enabled", s.enabled); arr.put(item); }
            catch (JSONException ignored) { }
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREFS_KEY, arr.toString()).apply();
        refreshInjection();
        web.reload();
    }

    private String extractSharedUrl(Intent intent, String fallback) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            String body = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (body != null) {
                Matcher m = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE).matcher(body);
                if (m.find()) return m.group();
            }
        }
        return fallback;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        web.loadUrl(extractSharedUrl(intent, START_URL));
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (documentStartHandler != null) { documentStartHandler.remove(); documentStartHandler = null; }
        if (webFileChooser != null) webFileChooser.onReceiveValue(null);
        if (web != null) { web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }

    private static final class UserScript {
        final String id;
        String source;
        boolean enabled;
        private static final Pattern HEADER = Pattern.compile(
                "(?m)^\\s*//\\s*@([A-Za-z-]+)\\s+(.+?)\\s*$");

        UserScript(String source, boolean enabled) {
            this(UUID.randomUUID().toString(), source, enabled);
        }

        UserScript(String id, String source, boolean enabled) {
            this.id = id;
            this.source = source;
            this.enabled = enabled;
        }

        private List<String> field(String key) {
            List<String> results = new ArrayList<>();
            Matcher m = HEADER.matcher(source);
            while (m.find()) if (key.equalsIgnoreCase(m.group(1))) results.add(m.group(2).trim());
            return results;
        }

        String name() {
            List<String> names = field("name");
            return names.isEmpty() ? "Adsız betik" : names.get(0);
        }

        String matchSummary() {
            List<String> patterns = field("match");
            if (patterns.isEmpty()) patterns = field("include");
            return patterns.isEmpty() ? "Bütün siteler" : patterns.get(0);
        }

        JSONObject metadata() {
            JSONObject obj = new JSONObject();
            try {
                // Kalıcı kimlik düzenleme sonrası da GM_getValue değerlerini korur.
                obj.put("id", id);
                obj.put("name", name());
                obj.put("enabled", enabled);
                obj.put("matches", new JSONArray(field("match")));
                obj.put("includes", new JSONArray(field("include")));
                obj.put("excludes", new JSONArray(field("exclude")));
                obj.put("excludeMatches", new JSONArray(field("exclude-match")));
                obj.put("noframes", source.contains("@noframes"));
                List<String> runAt = field("run-at");
                obj.put("runAt", runAt.isEmpty() ? "document-end" : runAt.get(0));
            } catch (JSONException ignored) { }
            return obj;
        }
    }
}
