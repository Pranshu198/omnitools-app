package com.jackbhai.omnitools;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

/** Builds + pushes RemoteViews for all three widgets from Store state. */
final class Widgets {
    private Widgets() {}

    static Intent openAppIntent(Context ctx, String route) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        if (route != null) i.putExtra(MainActivity.EXTRA_ROUTE, route);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return i;
    }

    private static PendingIntent openApp(Context ctx, String route, int req) {
        return PendingIntent.getActivity(ctx, req, openAppIntent(ctx, route),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent musicCmd(Context ctx, String cmd, int req) {
        Intent i = new Intent(ctx, CmdReceiver.class);
        i.setAction(CmdReceiver.ACTION);
        i.putExtra(CmdReceiver.EXTRA_CMD, cmd);
        return PendingIntent.getBroadcast(ctx, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ------------------------------------------------------------ music

    static RemoteViews buildMusic(Context ctx, boolean compact) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(),
                compact ? R.layout.widget_music_compact : R.layout.widget_music);
        boolean has = Store.bool(ctx, Store.M_HAS, false);
        boolean playing = Store.bool(ctx, Store.M_PLAYING, false);
        String title = Store.str(ctx, Store.M_TITLE, "");
        String artist = Store.str(ctx, Store.M_ARTIST, "");
        rv.setTextViewText(R.id.wTitle,
                has && !title.isEmpty() ? title : "Nothing playing yet");
        rv.setTextViewText(R.id.wArtist,
                has ? artist : ctx.getString(R.string.open_app_to_play));
        rv.setImageViewResource(R.id.wToggle, playing ? R.drawable.ic_pause : R.drawable.ic_play);
        rv.setOnClickPendingIntent(R.id.wToggle, musicCmd(ctx, "toggle", 11));
        rv.setOnClickPendingIntent(R.id.wNext, musicCmd(ctx, "next", 12));
        if (!compact) {
            rv.setOnClickPendingIntent(R.id.wPrev, musicCmd(ctx, "prev", 13));
        }
        // Tapping the text area opens the app at the music tool.
        rv.setOnClickPendingIntent(R.id.wTitle, openApp(ctx, "music", 14));
        return rv;
    }

    static void updateMusic(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        ComponentName cn = new ComponentName(ctx, MusicWidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        for (int id : ids) {
            Bundle opts = mgr.getAppWidgetOptions(id);
            int h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            boolean compact = h > 0 && h < 90;
            mgr.updateAppWidget(id, buildMusic(ctx, compact));
        }
    }

    // ------------------------------------------------------------ daily

    static RemoteViews buildDaily(Context ctx) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_daily);
        String verse = Store.str(ctx, Store.D_VERSE, "");
        String ref = Store.str(ctx, Store.D_REF, "");
        String tithi = Store.str(ctx, Store.D_TITHI, "");
        String nak = Store.str(ctx, Store.D_NAK, "");
        String date = Store.str(ctx, Store.D_DATE, "");
        if (!verse.isEmpty()) {
            rv.setTextViewText(R.id.wVerse, verse);
            rv.setTextViewText(R.id.wRef, ref);
        }
        StringBuilder p = new StringBuilder();
        if (!tithi.isEmpty()) p.append(tithi);
        if (!nak.isEmpty()) {
            if (p.length() > 0) p.append(" · ");
            p.append(nak).append(" nakshatra");
        }
        if (p.length() > 0) rv.setTextViewText(R.id.wPanchang, p.toString());
        if (!date.isEmpty()) rv.setTextViewText(R.id.wDate, date);
        rv.setOnClickPendingIntent(R.id.wVerseZone, openApp(ctx, "votd", 21));
        rv.setOnClickPendingIntent(R.id.wPanchangZone, openApp(ctx, "panchang", 22));
        return rv;
    }

    static void updateDaily(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        ComponentName cn = new ComponentName(ctx, DailyWidgetProvider.class);
        for (int id : mgr.getAppWidgetIds(cn)) {
            mgr.updateAppWidget(id, buildDaily(ctx));
        }
    }

    // ------------------------------------------------------------ quick

    static RemoteViews buildQuick(Context ctx) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_quick);
        rv.setOnClickPendingIntent(R.id.qTv, openApp(ctx, "tv", 31));
        rv.setOnClickPendingIntent(R.id.qDl, openApp(ctx, "dl", 32));
        rv.setOnClickPendingIntent(R.id.qRadio, openApp(ctx, "radio", 33));
        rv.setOnClickPendingIntent(R.id.qGames, openApp(ctx, "games", 34));
        rv.setOnClickPendingIntent(R.id.qTrain, openApp(ctx, "train", 35));
        return rv;
    }

    static void updateQuick(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        ComponentName cn = new ComponentName(ctx, QuickWidgetProvider.class);
        for (int id : mgr.getAppWidgetIds(cn)) {
            mgr.updateAppWidget(id, buildQuick(ctx));
        }
    }
}
