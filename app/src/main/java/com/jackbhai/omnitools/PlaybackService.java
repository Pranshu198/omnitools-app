package com.jackbhai.omnitools;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Foreground-service-lite: while something is playing, a lightweight
 * foreground service with a media notification keeps the process (and
 * with it the WebView's audio) alive when the user switches apps.
 * Started/stopped purely from the pushed playback state.
 */
public class PlaybackService extends Service {
    private static final String CHANNEL_ID = "playback";
    private static final int NOTIF_ID = 42;

    /** Start when playing, stop when paused/ended — called on every push. */
    static void syncWithState(Context ctx) {
        boolean playing = Store.bool(ctx, Store.M_PLAYING, false)
                && Store.bool(ctx, Store.M_HAS, false);
        Intent svc = new Intent(ctx, PlaybackService.class);
        if (playing) {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc);
            else ctx.startService(svc);
        } else {
            ctx.stopService(svc);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shown while music or radio is playing");
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String title = Store.str(this, Store.M_TITLE, "OmniTools");
        String artist = Store.str(this, Store.M_ARTIST, "");
        PendingIntent open = PendingIntent.getActivity(this, 41,
                Widgets.openAppIntent(this, "music"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_radio)
                .setContentTitle(title.isEmpty() ? "OmniTools" : title)
                .setContentText(artist)
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
