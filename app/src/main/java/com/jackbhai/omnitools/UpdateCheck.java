package com.jackbhai.omnitools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Once-a-day update check against this app's own GitHub Releases.
 *
 * The app is standalone (the whole site is bundled in the APK), so an
 * "update" is a newer APK on the omnitools-app releases page. The check
 * is best-effort: offline, throttled, or any parse failure = stay silent.
 * It never blocks startup and never auto-downloads anything.
 */
final class UpdateCheck {
    private static final String PREFS = "omni_update";
    private static final String KEY_LAST = "last_check_ms";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final String LATEST =
            "https://api.github.com/repos/Pranshu198/omnitools-app/releases/latest";

    private UpdateCheck() {}

    static void maybeCheck(Activity activity) {
        SharedPreferences sp = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - sp.getLong(KEY_LAST, 0) < DAY_MS) return;
        sp.edit().putLong(KEY_LAST, now).apply();

        final String current = currentVersion(activity);
        if (current == null) return;

        Thread t = new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(LATEST).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "OmniToolsApp/" + current);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                if (c.getResponseCode() != 200) return;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                JSONObject rel = new JSONObject(sb.toString());
                String tag = rel.optString("tag_name", "");          // e.g. "v2.1"
                String latest = tag.replaceFirst("^v", "");
                if (!isNewer(latest, current)) return;

                String apkUrl = null;
                JSONArray assets = rel.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.optJSONObject(i);
                        if (a != null && a.optString("name", "").endsWith(".apk")) {
                            apkUrl = a.optString("browser_download_url", null);
                            break;
                        }
                    }
                }
                final String download = apkUrl != null
                        ? apkUrl : rel.optString("html_url", "");
                if (download.isEmpty() || activity.isFinishing()) return;
                final String ver = latest;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    new AlertDialog.Builder(activity)
                            .setTitle("Update available")
                            .setMessage("OmniTools " + ver + " is ready. You have "
                                    + current + ". The update downloads as an APK "
                                    + "and installs over this app — your data stays.")
                            .setPositiveButton("Download", (d, w) -> {
                                try {
                                    activity.startActivity(new Intent(
                                            Intent.ACTION_VIEW, Uri.parse(download)));
                                } catch (Exception ignored) {
                                }
                            })
                            .setNegativeButton("Later", null)
                            .show();
                });
            } catch (Exception ignored) {
                // Offline or GitHub unreachable: silently skip, retry tomorrow.
            }
        }, "omni-update-check");
        t.setDaemon(true);
        t.start();
    }

    private static String currentVersion(Activity a) {
        try {
            PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return null;
        }
    }

    /** Numeric dotted compare: isNewer("2.1", "2.0") == true. */
    static boolean isNewer(String latest, String current) {
        if (latest == null || current == null) return false;
        String[] l = latest.split("\\."), c = current.split("\\.");
        for (int i = 0; i < Math.max(l.length, c.length); i++) {
            int li = i < l.length ? parse(l[i]) : 0;
            int ci = i < c.length ? parse(c[i]) : 0;
            if (li != ci) return li > ci;
        }
        return false;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
