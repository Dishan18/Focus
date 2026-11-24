package com.regain.app.core

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

object Permissions {

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** "Display over other apps": needed to show gates over other apps. */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Everything the engine needs to run. */
    fun ready(context: Context): Boolean = hasUsageAccess(context) && canDrawOverlays(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun usageAccessIntent(context: Context) =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun usageAccessFallbackIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlayIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun overlayFallbackIntent() = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)

    fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    @Suppress("BatteryLife")
    fun batteryIntent(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /** Launch an intent, falling back if the OEM removed the specific screen. */
    fun launch(context: Context, primary: Intent, fallback: Intent? = null) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        runCatching { context.startActivity(primary.addFlags(flags)) }
            .onFailure { fallback?.let { f -> runCatching { context.startActivity(f.addFlags(flags)) } } }
    }
}
