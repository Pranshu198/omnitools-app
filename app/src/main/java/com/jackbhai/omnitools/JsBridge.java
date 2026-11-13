package com.jackbhai.omnitools;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * The `window.OmniApp` object injected into the WebView. The PWA detects
 * it to enable app-mode, and pushes playback + daily content through it
 * so the native widgets and the playback service stay in sync.
 *
 * All methods are called on a WebView binder thread — UI work is posted
 * to the activity, file writes run inline (they can be large, that's
 * fine off the UI thread).
 */
public class JsBridge {
    private final Activity activity;

    JsBridge(Activity activity) {
        this.activity = activity;
    }

    /** Marker the site checks: true when running inside this app. */
    @JavascriptInterface
    public boolean isApp() {
        return true;
    }

    /** Native share sheet for text / a URL. */
    @JavascriptInterface
    public void share(String text) {
        if (text == null || text.isEmpty()) return;
        activity.runOnUiThread(() -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, text);
            activity.startActivity(Intent.createChooser(send, "Share via"));
        });
    }

    /**
     * Save a base64 payload into the device Downloads folder (MediaStore
     * on API 29+, legacy public Downloads on 26–28). Used by the blob
     * shim for the Downloader / offline flows, whose blob: URLs a plain
     * WebView cannot save on its own.
     */
    @JavascriptInterface
    public void saveFile(String base64, String filename, String mime) {
        String name = sanitize(filename);
        String type = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = activity.getContentResolver();
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, type);
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new IllegalStateException("MediaStore insert failed");
                try (OutputStream os = cr.openOutputStream(uri)) {
                    if (os == null) throw new IllegalStateException("No output stream");
                    os.write(bytes);
                }
                cv.clear();
                cv.put(MediaStore.Downloads.IS_PENDING, 0);
                cr.update(uri, cv, null, null);
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, name);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(bytes);
                }
            }
            toast("Saved " + name + " to Downloads");
        } catch (Exception e) {
            toast("Save failed: " + name);
        }
    }

    /**
     * Playback state pushed by the site's player:
     * {title, artist, playing, hasTrack}. Drives the music widget and
     * the foreground playback service.
     */
    @JavascriptInterface
    public void musicState(String json) {
        try {
            JSONObject o = new JSONObject(json == null ? "{}" : json);
            Context ctx = activity.getApplicationContext();
            Store.get(ctx).edit()
                    .putString(Store.M_TITLE, o.optString("title", ""))
                    .putString(Store.M_ARTIST, o.optString("artist", ""))
                    .putBoolean(Store.M_PLAYING, o.optBoolean("playing", false))
                    .putBoolean(Store.M_HAS, o.optBoolean("hasTrack", false))
                    .apply();
            Widgets.updateMusic(ctx);
            PlaybackService.syncWithState(ctx);
        } catch (Exception ignored) {
            // A malformed push must never crash the page's JS thread.
        }
    }

    /**
     * Today's content pushed by the site:
     * {date, dateKey, verseRef, verseText, tithi, nakshatra}.
     * Drives the Daily widget.
     */
    @JavascriptInterface
    public void dailyPush(String json) {
        try {
            JSONObject o = new JSONObject(json == null ? "{}" : json);
            Context ctx = activity.getApplicationContext();
            Store.get(ctx).edit()
                    .putString(Store.D_DATE, o.optString("date", ""))
                    .putString(Store.D_DATEKEY, o.optString("dateKey", ""))
                    .putString(Store.D_VERSE, o.optString("verseText", ""))
                    .putString(Store.D_REF, o.optString("verseRef", ""))
                    .putString(Store.D_TITHI, o.optString("tithi", ""))
                    .putString(Store.D_NAK, o.optString("nakshatra", ""))
                    .apply();
            Widgets.updateDaily(ctx);
        } catch (Exception ignored) {
        }
    }

    /**
     * Receive path for widget commands: forwards to the same dispatcher
     * the widget broadcasts use (window.__omniMusicCmd in the page).
     * cmd: "toggle" | "next" | "prev".
     */
    @JavascriptInterface
    public void musicCmd(String cmd) {
        MainActivity.dispatchMusicCmd(cmd);
    }

    /** Short vibration for app-mode interactions (tab switch, edit entry). */
    @JavascriptInterface
    public void haptic(int ms) {
        try {
            android.os.Vibrator v = (android.os.Vibrator)
                    activity.getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            int clamped = Math.max(1, Math.min(ms, 120));
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(android.os.VibrationEffect.createOneShot(
                        clamped, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(clamped);
            }
        } catch (Exception ignored) {
        }
    }

    /** Keep the screen on (e.g. while the full player is open). */
    @JavascriptInterface
    public void keepAwake(boolean on) {
        activity.runOnUiThread(() -> {
            if (on) {
                activity.getWindow().addFlags(
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                activity.getWindow().clearFlags(
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    private void toast(String msg) {
        activity.runOnUiThread(() ->
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show());
    }

    private static String sanitize(String name) {
        if (name == null || name.trim().isEmpty()) return "download";
        String n = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return n.isEmpty() ? "download" : n;
    }
}
