package com.quickvolume.app

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import kotlin.math.roundToInt

enum class VolumeMode(
    val label: String,
    val media: Int,
    val notification: Int,
    val call: Int,
    val alarm: Int,
) {
    NORMAL("Normal", 70, 70, 80, 80),
    SILENT("Silent", 0, 0, 0, 0),
    LOUD("Loud", 100, 100, 100, 100),
    MUTE("Mute Total", 0, 0, 0, 0),
}

data class ApplyResult(val ok: Boolean, val message: String)

object VolumeController {

    private val MUTE_KEYS = listOf(
        "vibrate_when_ringing",
        Settings.System.HAPTIC_FEEDBACK_ENABLED,
        Settings.System.SOUND_EFFECTS_ENABLED,
    )

    val streams = listOf(
        "Media" to AudioManager.STREAM_MUSIC,
        "Notifikasi" to AudioManager.STREAM_NOTIFICATION,
        "Panggilan" to AudioManager.STREAM_VOICE_CALL,
        "Alarm" to AudioManager.STREAM_ALARM,
    )

    fun readPercent(context: Context, stream: Int): Int {
        val am = context.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
        return (am.getStreamVolume(stream) * 100f / max).roundToInt()
    }

    fun apply(context: Context, mode: VolumeMode): ApplyResult {
        val am = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        val hasDnd = nm.isNotificationPolicyAccessGranted
        val problems = mutableListOf<String>()

        val prefs = context.getSharedPreferences("qvc", Context.MODE_PRIVATE)
        val canWrite = Settings.System.canWrite(context)
        val silentLike = mode == VolumeMode.SILENT || mode == VolumeMode.MUTE

        // Keluar dari Mute Total: pulihkan pengaturan getar/haptic semula.
        if (mode != VolumeMode.MUTE && prefs.getBoolean("muted", false) && canWrite) {
            for (key in MUTE_KEYS) {
                Settings.System.putInt(context.contentResolver, key, prefs.getInt("saved_$key", 1))
            }
            prefs.edit().putBoolean("muted", false).apply()
        }

        // Keluar dari Silent: matikan DND lebih dulu supaya volume bisa dinaikkan.
        if (mode != VolumeMode.SILENT && hasDnd) {
            try {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            } catch (e: SecurityException) {
                problems += "Mode Jangan Ganggu tidak dapat dimatikan"
            }
        }

        val targets = listOf(
            Triple("Media", AudioManager.STREAM_MUSIC, mode.media),
            Triple("Notifikasi", AudioManager.STREAM_NOTIFICATION, mode.notification),
            Triple("Panggilan", AudioManager.STREAM_VOICE_CALL, mode.call),
            Triple("Alarm", AudioManager.STREAM_ALARM, mode.alarm),
        )
        for ((name, stream, pct) in targets) setPercent(am, stream, name, pct, problems)

        if (mode == VolumeMode.MUTE) {
            // Stream tambahan: dering, sistem, DTMF (kegagalan diabaikan).
            for (s in listOf(AudioManager.STREAM_RING, AudioManager.STREAM_SYSTEM, AudioManager.STREAM_DTMF)) {
                try { am.setStreamVolume(s, 0, 0) } catch (e: SecurityException) { }
            }
            // Matikan getar saat berdering, haptic, dan suara sentuh.
            if (canWrite) {
                if (!prefs.getBoolean("muted", false)) {
                    val ed = prefs.edit().putBoolean("muted", true)
                    for (key in MUTE_KEYS) {
                        ed.putInt("saved_$key", Settings.System.getInt(context.contentResolver, key, 1))
                    }
                    ed.apply()
                }
                for (key in MUTE_KEYS) Settings.System.putInt(context.contentResolver, key, 0)
            } else {
                problems += "getar/haptic belum dimatikan: izin ubah pengaturan sistem belum diberikan"
            }
        }

        if (silentLike) {
            if (hasDnd) {
                try {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                    am.ringerMode = AudioManager.RINGER_MODE_SILENT
                } catch (e: SecurityException) {
                    problems += "DND tidak dapat diaktifkan"
                }
            } else {
                problems += "izin Jangan Ganggu (DND) belum diberikan, DND tidak aktif"
            }
        }

        return if (problems.isEmpty()) {
            ApplyResult(true, "Mode ${mode.label} diterapkan.")
        } else {
            ApplyResult(false, "Mode ${mode.label} diterapkan sebagian: ${problems.joinToString("; ")}.")
        }
    }

    private fun setPercent(
        am: AudioManager,
        stream: Int,
        name: String,
        pct: Int,
        problems: MutableList<String>,
    ) {
        val max = am.getStreamMaxVolume(stream)
        val min = if (Build.VERSION.SDK_INT >= 28) {
            am.getStreamMinVolume(stream)
        } else if (stream == AudioManager.STREAM_VOICE_CALL) {
            1
        } else {
            0
        }
        val index = (pct / 100f * max).roundToInt().coerceIn(min, max)
        try {
            am.setStreamVolume(stream, index, 0)
        } catch (e: SecurityException) {
            problems += "$name ditolak sistem (butuh izin Jangan Ganggu)"
            return
        }
        if (am.getStreamVolume(stream) != index) {
            problems += "$name tertahan oleh sistem"
        } else if (pct == 0 && index > 0) {
            problems += "$name tidak bisa 0% di perangkat ini (minimum sistem)"
        }
    }
}
