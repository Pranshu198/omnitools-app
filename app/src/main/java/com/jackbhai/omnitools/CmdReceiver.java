package com.jackbhai.omnitools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives widget music commands. If the app's WebView is alive the
 * command goes straight into the page's player; otherwise the app is
 * opened at the Music tool with the command queued for page load.
 */
public class CmdReceiver extends BroadcastReceiver {
    static final String ACTION = "com.jackbhai.omnitools.MUSIC_CMD";
    static final String EXTRA_CMD = "cmd";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String cmd = intent.getStringExtra(EXTRA_CMD);
        if (cmd == null) return;
        if (MainActivity.isWebAlive()) {
            MainActivity.dispatchMusicCmd(cmd);
        } else {
            Intent open = Widgets.openAppIntent(ctx, "music");
            open.putExtra(MainActivity.EXTRA_CMD, cmd);
            ctx.startActivity(open);
        }
    }
}
