package com.jackbhai.omnitools;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The app shell: a full-screen WebView running the OmniTools PWA, with
 * the native pieces a browser tab cannot give it — the OmniApp JS
 * bridge, blob-download saving, camera/file uploads, media permissions,
 * deep links, external-link intents and widget command dispatch.
 *
 * The WebView is deliberately NOT paused with the activity: background
 * audio (music / radio) must keep playing when the user switches apps,
 * and the foreground PlaybackService keeps the process alive meanwhile.
 */
public class MainActivity extends Activity {
    static final String EXTRA_ROUTE = "route";   // in-app hash, e.g. "tv"
    static final String EXTRA_CMD = "cmd";       // queued music command
    private static final String BASE = "https://jackbhai.github.io/omnitools/";
    private static final int REQ_FILE = 1001;
    private static final int REQ_PERM = 1002;

    private static MainActivity instance;
    private static WebView web;
    private static boolean pageLoaded;

    private String pendingCmd;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private PermissionRequest pendingPermReq;

    // ------------------------------------------------------------ bridge out

    static boolean isWebAlive() {
        return instance != null && web != null && pageLoaded;
    }

    /** Runs window.__omniMusicCmd(cmd) in the page (registered by the PWA player). */
    static void dispatchMusicCmd(String cmd) {
        if (cmd == null || !cmd.matches("[a-z]+")) return;
        final MainActivity a = instance;
        if (a == null || web == null) return;
        a.runOnUiThread(() -> {
            if (web != null) {
                web.evaluateJavascript(
                        "window.__omniMusicCmd&&window.__omniMusicCmd('" + cmd + "')", null);
            }
        });
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);   // swap splash theme for the real one
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        instance = this;

        web = findViewById(R.id.webview);
        setupWebView(web);

        String route = routeFromIntent(getIntent());
        pendingCmd = getIntent() != null ? getIntent().getStringExtra(EXTRA_CMD) : null;
        web.loadUrl(route != null ? BASE + "#" + route : BASE);

        findViewById(R.id.retryBtn).setOnClickListener(v -> {
            findViewById(R.id.errorBox).setVisibility(View.GONE);
            if (web != null) web.reload();
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String route = routeFromIntent(intent);
        String cmd = intent.getStringExtra(EXTRA_CMD);
        if (cmd != null) pendingCmd = cmd;
        if (route != null && web != null && pageLoaded) {
            web.evaluateJavascript(
                    "window.location.hash=" + jsString("#" + route), null);
            applyPendingCmdSoon();
        } else if (route != null && web != null) {
            web.loadUrl(BASE + "#" + route);
        } else {
            applyPendingCmdSoon();
        }
    }

    @Override
    protected void onDestroy() {
        if (instance == this) {
            instance = null;
            pageLoaded = false;
            web = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /** Route from omnitools://tool/<hash>, or the "route" extra (widgets). */
    private String routeFromIntent(Intent intent) {
        if (intent == null) return null;
        String extra = intent.getStringExtra(EXTRA_ROUTE);
        if (extra != null && !extra.isEmpty()) return extra.replaceFirst("^#", "");
        Uri data = intent.getData();
        if (data != null && "omnitools".equals(data.getScheme())
                && "tool".equals(data.getHost())) {
            String seg = data.getLastPathSegment();
            if (seg != null && !seg.isEmpty()) return seg;
        }
        return null;
    }

    // ------------------------------------------------------------ webview setup

    private void setupWebView(WebView wv) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setUserAgentString(s.getUserAgentString() + " OmniToolsApp/1.0");
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

        wv.addJavascriptInterface(new JsBridge(this), "OmniApp");
        wv.setWebViewClient(new ShellClient());
        wv.setWebChromeClient(new ShellChrome());
        wv.setDownloadListener(downloadListener);
    }

    private final DownloadListener downloadListener = (url, userAgent, contentDisposition, mimeType, contentLength) -> {
        try {
            String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setMimeType(mimeType);
            req.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) req.addRequestHeader("Cookie", cookie);
            req.setTitle(name);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) dm.enqueue(req);
            Toast.makeText(this, "Downloading " + name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show();
        }
    };

    private class ShellClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(request.getUrl());
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(Uri.parse(url));
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            pageLoaded = true;
            view.evaluateJavascript(BLOB_SHIM, null);
            applyPendingCmdSoon();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                findViewById(R.id.errorBox).setVisibility(View.VISIBLE);
            }
        }
    }

    /** true = handled outside the WebView. */
    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        switch (scheme) {
            case "omnitools": {
                String seg = uri.getLastPathSegment();
                if (seg != null && web != null) {
                    web.evaluateJavascript("window.location.hash=" + jsString("#" + seg), null);
                }
                return true;
            }
            case "tel": case "mailto": case "sms": case "smsto":
            case "upi": case "whatsapp": case "intent": case "market":
                try {
                    Intent i = "intent".equals(scheme)
                            ? Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                            : new Intent(Intent.ACTION_VIEW, uri);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception ignored) {
                }
                return true;
            case "http": case "https": {
                String host = uri.getHost();
                if (host != null && host.endsWith("jackbhai.github.io")) return false;
                // Everything else leaves the app shell (source links, shares).
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, uri);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception ignored) {
                }
                return true;
            }
            default:
                return false;   // blob:, data:, about: — the WebView's business
        }
    }

    private void applyPendingCmdSoon() {
        if (pendingCmd == null || web == null) return;
        final String cmd = pendingCmd;
        pendingCmd = null;
        // The player registers __omniMusicCmd after first render; retry once.
        web.postDelayed(() -> dispatchMusicCmd(cmd), 900);
        web.postDelayed(() -> dispatchMusicCmd(cmd), 2600);
    }

    // ------------------------------------------------------------ chrome: files + media perms

    private class ShellChrome extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;

            String accept = "*/*";
            String[] types = params.getAcceptTypes();
            if (types != null) {
                for (String t : types) {
                    if (t == null) continue;
                    if (t.contains("image")) { accept = "image/*"; break; }
                    if (t.contains("audio")) { accept = "audio/*"; break; }
                    if (t.contains("video")) { accept = "video/*"; break; }
                }
            }

            Intent content = new Intent(Intent.ACTION_GET_CONTENT);
            content.addCategory(Intent.CATEGORY_OPENABLE);
            content.setType(accept);
            content.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);

            Intent chooser;
            if (params.isCaptureEnabled() || "image/*".equals(accept)) {
                Intent camera = buildCameraIntent();
                if (params.isCaptureEnabled() && camera != null) {
                    startActivityForResult(camera, REQ_FILE);
                    return true;
                }
                chooser = Intent.createChooser(content, "Add a photo or file");
                if (camera != null) {
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                }
            } else {
                chooser = Intent.createChooser(content, "Choose a file");
            }
            try {
                startActivityForResult(chooser, REQ_FILE);
            } catch (Exception e) {
                fileCallback = null;
                return false;
            }
            return true;
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            List<String> needed = new ArrayList<>();
            for (String res : request.getResources()) {
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)
                        && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.RECORD_AUDIO);
                }
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)
                        && checkSelfPermission(Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.CAMERA);
                }
            }
            if (needed.isEmpty()) {
                request.grant(request.getResources());
            } else {
                pendingPermReq = request;
                requestPermissions(needed.toArray(new String[0]), REQ_PERM);
            }
        }
    }

    private Intent buildCameraIntent() {
        try {
            Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (camera.resolveActivity(getPackageManager()) == null) return null;
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, "omni_scan_" + System.currentTimeMillis() + ".jpg");
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) {
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            }
            cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (cameraUri == null) return null;
            camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return camera;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE || fileCallback == null) return;
        Uri[] results = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getClipData() != null) {
                ClipData clip = data.getClipData();
                results = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) results[i] = clip.getItemAt(i).getUri();
            } else if (data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            } else if (cameraUri != null) {
                results = new Uri[]{cameraUri};
            }
        }
        fileCallback.onReceiveValue(results);
        fileCallback = null;
        cameraUri = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM && pendingPermReq != null) {
            boolean all = grantResults.length > 0;
            for (int g : grantResults) all = all && g == PackageManager.PERMISSION_GRANTED;
            if (all) pendingPermReq.grant(pendingPermReq.getResources());
            else pendingPermReq.deny();
            pendingPermReq = null;
        }
    }

    private static String jsString(String v) {
        return "'" + v.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /**
     * Blob-download shim: the site's Downloader / offline flows hand the
     * user an <a download href="blob:…">. A plain WebView drops those.
     * In the app we intercept the click, read the blob, and route it to
     * OmniApp.saveFile → device Downloads.
     */
    private static final String BLOB_SHIM =
            "(function(){"
            + "if(window.__omniBlobShim)return;window.__omniBlobShim=true;"
            + "document.addEventListener('click',function(e){"
            + "var a=e.target&&e.target.closest?e.target.closest('a[download]'):null;"
            + "if(!a)return;"
            + "var href=a.getAttribute('href')||'';"
            + "if(href.indexOf('blob:')!==0)return;"
            + "e.preventDefault();e.stopPropagation();"
            + "var name=a.getAttribute('download')||'download';"
            + "fetch(href).then(function(r){return r.blob();}).then(function(b){"
            + "var fr=new FileReader();"
            + "fr.onload=function(){var d=fr.result||'';var c=d.indexOf(',');"
            + "window.OmniApp.saveFile(c>=0?d.slice(c+1):'',name,b.type||'application/octet-stream');};"
            + "fr.readAsDataURL(b);"
            + "}).catch(function(){});"
            + "},true);"
            + "})();";
}
