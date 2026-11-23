package com.regain.app.core

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.regain.app.data.Store

/**
 * Optional "colours drain away" effect using Android's colour-correction
 * (monochrome) setting. Needs WRITE_SECURE_SETTINGS, granted once over adb:
 *   adb shell pm grant com.regain.app android.permission.WRITE_SECURE_SETTINGS
 *
 * We only ever undo what we changed, and restore on every chance we get, so
 * the phone can never be left stuck in grayscale.
 */
object Grayscale {
    private const val KEY_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val KEY_MODE = "accessibility_display_daltonizer"
    private const val MONOCHROME = 0

    fun available(context: Context): Boolean =
        context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    fun on(context: Context) {
        if (!available(context)) return
        val p = Store.rawPrefs()
        if (p.getBoolean("grayActive", false)) return
        val cr = context.contentResolver
        runCatching {
            val prevEnabled = Settings.Secure.getInt(cr, KEY_ENABLED, 0)
            val prevMode = Settings.Secure.getInt(cr, KEY_MODE, -1)
            p.edit().putBoolean("grayActive", true).putInt("grayPrevEnabled", prevEnabled).putInt("grayPrevMode", prevMode).commit()
            Settings.Secure.putInt(cr, KEY_MODE, MONOCHROME)
            Settings.Secure.putInt(cr, KEY_ENABLED, 1)
        }
    }

    fun off(context: Context) {
        val p = Store.rawPrefs()
        if (!p.getBoolean("grayActive", false)) return
        val cr = context.contentResolver
        runCatching {
            val prevMode = p.getInt("grayPrevMode", -1)
            Settings.Secure.putInt(cr, KEY_ENABLED, p.getInt("grayPrevEnabled", 0))
            if (prevMode >= 0) Settings.Secure.putInt(cr, KEY_MODE, prevMode)
        }
        p.edit().putBoolean("grayActive", false).apply()
    }
}
