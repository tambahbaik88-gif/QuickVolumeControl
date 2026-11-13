package com.quickvolume.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class BrightnessGuardService : Service() {

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            enforce()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifs.ensureChannels(this)
        ServiceCompat.startForeground(
            this,
            Notifs.ID_GUARD,
            Notifs.guardNotification(this),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false,
            observer,
        )
        enforce()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        super.onDestroy()
    }

    private fun enforce() {
        val value = BrightnessController.current(this)
        if (value >= BrightnessController.MAX) {
            Notifs.brightnessWarning(this, BrightnessController.canWrite(this))
        }
        if (value != BrightnessController.TARGET) {
            BrightnessController.setTarget(this)
        }
    }

    companion object {
        private const val PREFS = "qvc"
        private const val KEY = "guard_enabled"

        fun isEnabled(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

        fun start(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
            ContextCompat.startForegroundService(context, Intent(context, BrightnessGuardService::class.java))
        }

        fun stop(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, false).apply()
            context.stopService(Intent(context, BrightnessGuardService::class.java))
        }
    }
}
