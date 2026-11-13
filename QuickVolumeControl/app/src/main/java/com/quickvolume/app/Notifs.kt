package com.quickvolume.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifs {
    const val CH_GUARD = "guard"
    const val CH_ALERT = "alert"
    const val ID_GUARD = 1
    const val ID_ALERT = 2

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_GUARD, "Penjaga kecerahan", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Peringatan kecerahan", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun guardNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CH_GUARD)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Penjaga kecerahan aktif")
            .setContentText("Menjaga kecerahan layar di 50%")
            .setOngoing(true)
            .build()

    fun brightnessWarning(context: Context, canFix: Boolean) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val text = if (canFix) {
            "Kecerahan layar mencapai 100%. Aplikasi mengembalikan kecerahan ke target 50%."
        } else {
            "Kecerahan layar mencapai 100%. Izin sistem belum diberikan, jadi kecerahan tidak bisa dikembalikan ke 50%."
        }
        val n = NotificationCompat.Builder(context, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("⚠ Peringatan Kecerahan")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(ID_ALERT, n)
        } catch (e: SecurityException) {
            // Izin notifikasi dicabut; abaikan.
        }
    }
}
