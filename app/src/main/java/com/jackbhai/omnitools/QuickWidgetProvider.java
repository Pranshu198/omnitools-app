package com.jackbhai.omnitools;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** Quick Tools widget: one-tap deep links into TV / Downloader / Radio / Games / Trains. */
public class QuickWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, Widgets.buildQuick(ctx));
    }
}
