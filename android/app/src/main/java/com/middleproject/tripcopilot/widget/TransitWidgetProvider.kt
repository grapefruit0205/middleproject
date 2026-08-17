package com.middleproject.tripcopilot.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.middleproject.tripcopilot.R
import com.middleproject.tripcopilot.TripCopilotApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TransitWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { refresh(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) refresh(context, AppWidgetManager.getInstance(context), id)
        }
    }

    private fun refresh(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.transit_widget)
        views.setTextViewText(R.id.widget_content, "Refreshing…")
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context, id))
        manager.updateAppWidget(id, views)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val text = runCatching {
                val repository = (context.applicationContext as TripCopilotApplication).repository
                val favorite = repository.transitFavorites().firstOrNull()
                    ?: return@runCatching "Add a subway or bus favorite first"
                val result = if (favorite.mode == "SUBWAY") {
                    repository.realtimeSubwayArrivals(favorite.stationName.orEmpty())
                } else {
                    repository.busArrivals(favorite.cityCode ?: 0, favorite.nodeId.orEmpty())
                }
                val title = listOfNotNull(favorite.alias, favorite.routeNo).joinToString(" · ")
                if (result.success) WidgetContentFormatter.format(title, result.items)
                else result.errorMessage ?: "Arrival lookup unavailable"
            }.getOrElse { "Open Trip Copilot and pair again" }
            views.setTextViewText(R.id.widget_content, text)
            views.setTextViewText(R.id.widget_updated, "Updated ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}")
            manager.updateAppWidget(id, views)
            pending.finish()
        }
    }

    private fun refreshIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, TransitWidgetProvider::class.java)
            .setAction(ACTION_REFRESH)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object { private const val ACTION_REFRESH = "com.middleproject.tripcopilot.widget.REFRESH" }
}
