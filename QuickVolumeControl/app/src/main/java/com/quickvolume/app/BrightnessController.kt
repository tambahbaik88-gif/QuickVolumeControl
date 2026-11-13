package com.quickvolume.app

import android.content.Context
import android.provider.Settings
import kotlin.math.roundToInt

object BrightnessController {
    const val MAX = 255
    const val TARGET = 128 // ~50%

    fun canWrite(context: Context) = Settings.System.canWrite(context)

    fun current(context: Context): Int = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (e: Settings.SettingNotFoundException) {
        TARGET
    }

    fun currentPercent(context: Context) = (current(context) * 100f / MAX).roundToInt()

    fun setTarget(context: Context): ApplyResult {
        if (!canWrite(context)) {
            return ApplyResult(false, "Kecerahan belum diubah: izin ubah pengaturan sistem belum diberikan.")
        }
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, TARGET)
            ApplyResult(true, "Kecerahan diatur ke 50%.")
        } catch (e: SecurityException) {
            ApplyResult(false, "Kecerahan ditolak oleh sistem.")
        }
    }
}
