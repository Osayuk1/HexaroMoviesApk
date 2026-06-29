package com.example.ui.widget

import android.app.PendingIntent
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class MediaAppWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_media_controller)

            // Deep link action to launch search tab
            val searchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("widget_action", "search")
            }
            val searchPendingIntent = PendingIntent.getActivity(
                context, 101, searchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_search_btn, searchPendingIntent)

            // Deep link action to launch home screen directly
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("widget_action", "home")
            }
            val mainPendingIntent = PendingIntent.getActivity(
                context, 102, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_logo_container, mainPendingIntent)
            remoteViews.setOnClickPendingIntent(R.id.widget_open_app_btn, mainPendingIntent)

            // Update widget view
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }
}
