package com.regain.app.core

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.regain.app.service.GuardService

object Permissions {

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasAccessibility(context: Context): Boolean {
        val expected = ComponentName(context, GuardService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it)?.let { c -> c == expected } == true
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun usageAccessIntent(context: Context) =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun usageAccessFallbackIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

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
