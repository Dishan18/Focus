package com.regain.app.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.regain.app.BuildConfig
import com.regain.app.data.Store
import java.util.concurrent.Executors

/**
 * Accountability buddy: a plain SMS to one trusted number when something
 * "loosening" happens (emergency unlock, ending focus early, removing a
 * limit). Nothing else is ever sent, and no internet is used.
 */
object Buddy {
    private val io = Executors.newSingleThreadExecutor()

    /** The number in use: what the user saved, or the default baked into the build. */
    fun number(): String = Store.buddyNumber.ifBlank { BuildConfig.BUDDY_PHONE }

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun isActive(context: Context): Boolean =
        Store.buddyOn && number().isNotBlank() && hasPermission(context)

    /** Fire-and-forget. Never throws, never blocks the UI. */
    fun notify(context: Context, message: String, to: String = number()) {
        if (!Store.buddyOn || to.isBlank() || !hasPermission(context)) return
        val app = context.applicationContext
        io.execute {
            runCatching {
                val sms = smsManager(app)
                val text = "Focus: $message"
                val parts = sms.divideMessage(text)
                if (parts.size > 1) sms.sendMultipartTextMessage(to, null, parts, null, null)
                else sms.sendTextMessage(to, null, text, null, null)
            }.onFailure { if (BuildConfig.DEBUG) Log.w("FocusBuddy", "SMS failed", it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context): SmsManager {
        val sub = SubscriptionManager.getDefaultSmsSubscriptionId()
        return if (Build.VERSION.SDK_INT >= 31) {
            val base = context.getSystemService(SmsManager::class.java)
            if (sub != SubscriptionManager.INVALID_SUBSCRIPTION_ID) base.createForSubscriptionId(sub) else base
        } else {
            if (sub != SubscriptionManager.INVALID_SUBSCRIPTION_ID) SmsManager.getSmsManagerForSubscriptionId(sub)
            else SmsManager.getDefault()
        }
    }
}
