package com.jackbhai.omnitools;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/**
 * Daily widget: Verse of the Day + Panchang headline. Content is pushed
 * by the site (OmniApp.dailyPush) whenever the app runs; the 30-minute
 * system refresh simply re-renders the latest stored values, and the
 * widget always shows the date those values belong to.
 */
public class DailyWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, Widgets.buildDaily(ctx));
    }
}
