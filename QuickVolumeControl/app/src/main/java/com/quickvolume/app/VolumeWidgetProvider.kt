package com.quickvolume.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast

class VolumeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, buildViews(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val result = when (intent.action) {
            ACTION_MODE -> {
                val name = intent.getStringExtra(EXTRA_MODE) ?: return
                VolumeController.apply(context, VolumeMode.valueOf(name))
            }
            ACTION_BRIGHTNESS -> BrightnessController.setTarget(context)
            else -> return
        }
        Toast.makeText(context.applicationContext, result.message, Toast.LENGTH_LONG).show()
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_volume)
        views.setOnClickPendingIntent(R.id.btn_normal, modeIntent(context, VolumeMode.NORMAL, 1))
        views.setOnClickPendingIntent(R.id.btn_silent, modeIntent(context, VolumeMode.SILENT, 2))
        views.setOnClickPendingIntent(R.id.btn_loud, modeIntent(context, VolumeMode.LOUD, 3))
        views.setOnClickPendingIntent(R.id.btn_mute, modeIntent(context, VolumeMode.MUTE, 5))
        val b = Intent(context, VolumeWidgetProvider::class.java).setAction(ACTION_BRIGHTNESS)
        views.setOnClickPendingIntent(R.id.btn_bright, broadcast(context, 4, b))
        return views
    }

    private fun modeIntent(context: Context, mode: VolumeMode, code: Int): PendingIntent {
        val i = Intent(context, VolumeWidgetProvider::class.java)
            .setAction(ACTION_MODE)
            .putExtra(EXTRA_MODE, mode.name)
        return broadcast(context, code, i)
    }

    private fun broadcast(context: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ACTION_MODE = "com.quickvolume.app.ACTION_MODE"
        const val ACTION_BRIGHTNESS = "com.quickvolume.app.ACTION_BRIGHTNESS"
        const val EXTRA_MODE = "mode"
    }
}
