package com.jackbhai.omnitools;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.widget.RemoteViews;

/** Music widget: 4x1 compact / 4x2 full, chosen from the widget's height. */
public class MusicWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) updateOne(ctx, mgr, id, mgr.getAppWidgetOptions(id));
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle newOptions) {
        updateOne(ctx, mgr, id, newOptions);
    }

    private void updateOne(Context ctx, AppWidgetManager mgr, int id, Bundle opts) {
        int h = opts != null ? opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) : 0;
        RemoteViews rv = Widgets.buildMusic(ctx, h > 0 && h < 90);
        mgr.updateAppWidget(id, rv);
    }
}
