package com.regain.app.ui.shield

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.regain.app.data.Store
import com.regain.app.service.GuardService
import com.regain.app.ui.theme.RegainTheme

enum class ShieldMode { Pause, TimesUp, Focus, Downtime, Morning }

/**
 * The full-screen gate shown over a limited app. Lives in its own task so
 * finishing it reveals the app underneath, and leaving it resets any
 * half-done challenge.
 */
class ShieldActivity : ComponentActivity() {

    private val mode = mutableStateOf(ShieldMode.Pause)
    private val pkg = mutableStateOf("")
    /** Budget key: the package itself, or "g:<id>" for a group. */
    private val key = mutableStateOf("")
    /** Bumped on every new launch so screen state starts fresh. */
    private val session = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        readIntent(intent)
        setContent {
            RegainTheme {
                BackHandler { goHome(reclaimed = false) }
                ShieldScreen(
                    mode = mode.value,
                    pkg = pkg.value,
                    budgetKey = key.value,
                    session = session.value,
                    actions = actions,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(i: Intent) {
        mode.value = runCatching { ShieldMode.valueOf(i.getStringExtra(EXTRA_MODE) ?: "") }.getOrDefault(ShieldMode.Pause)
        pkg.value = i.getStringExtra(EXTRA_PKG).orEmpty()
        key.value = i.getStringExtra(EXTRA_KEY) ?: pkg.value
        session.value++
    }

    override fun onResume() {
        super.onResume()
        GuardService.ownUiResumed()
    }

    override fun onPause() {
        super.onPause()
        GuardService.ownUiPaused()
    }

    override fun onStop() {
        super.onStop()
        // Walking away (home, recents, calculator...) abandons the gate.
        if (!isChangingConfigurations && !isFinishing) finish()
    }

    private val actions = object : ShieldActions {
        override fun goHome(reclaimed: Boolean) = this@ShieldActivity.goHome(reclaimed)

        override fun allow() {
            GuardService.grantPass(pkg.value)
            finish()
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }

    private fun goHome(reclaimed: Boolean) {
        if (reclaimed) Store.recordReclaim()
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
        finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_KEY = "key"

        fun intent(context: Context, mode: ShieldMode, pkg: String, key: String = pkg) =
            Intent(context, ShieldActivity::class.java)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_KEY, key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

interface ShieldActions {
    /** Leave to the home screen. [reclaimed] counts it as a win. */
    fun goHome(reclaimed: Boolean)
    /** Let the app through until the user leaves it. */
    fun allow()
}
