package com.regain.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the engine after a reboot and after Focus is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> GuardService.start(context)
        }
    }
}
