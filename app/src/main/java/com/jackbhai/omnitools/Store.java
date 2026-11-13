package com.jackbhai.omnitools;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Tiny SharedPreferences store shared by the JS bridge (writer), the
 * widgets (readers) and the playback service. All state the home-screen
 * widgets show lives here — the WebView pushes it, the widgets read it.
 */
final class Store {
    private static final String PREFS = "omni_state";

    // Music
    static final String M_TITLE = "mus_title";
    static final String M_ARTIST = "mus_artist";
    static final String M_PLAYING = "mus_playing";
    static final String M_HAS = "mus_has";

    // Daily (verse of the day + panchang headline)
    static final String D_DATE = "day_date";       // e.g. "Sat, 14 Nov 2026"
    static final String D_DATEKEY = "day_datekey"; // yyyy-MM-dd the values belong to
    static final String D_VERSE = "day_verse";
    static final String D_REF = "day_ref";
    static final String D_TITHI = "day_tithi";
    static final String D_NAK = "day_nakshatra";

    private Store() {}

    static SharedPreferences get(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String str(Context ctx, String key, String def) {
        return get(ctx).getString(key, def);
    }

    static boolean bool(Context ctx, String key, boolean def) {
        return get(ctx).getBoolean(key, def);
    }
}
