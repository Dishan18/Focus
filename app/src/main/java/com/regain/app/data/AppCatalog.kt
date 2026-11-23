package com.regain.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Installed launchable apps, with small cached labels and icons. */
class AppCatalog(private val context: Context) {

    data class AppInfo(val pkg: String, val label: String)

    private val pm = context.packageManager
    private val labelCache = HashMap<String, String>()
    private val iconCache = object : LruCache<String, ImageBitmap>(48) {}

    @Volatile private var launchableCache: Set<String>? = null

    /** Common time sinks, surfaced as suggestions if installed. */
    val suggested = listOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.facebook.katana",
        "com.snapchat.android",
        "com.twitter.android",
        "com.reddit.frontpage",
        "com.pinterest",
        "in.mohalla.sharechat",
        "com.moj.app",
        "com.netflix.mediaclient",
        "in.startv.hotstar",
        "com.facebook.orca",
        "com.whatsapp",
        "org.telegram.messenger",
    )

    fun launchableApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
        launchableCache = list.toSet()
        return list.map { AppInfo(it, label(it)) }.sortedBy { it.label.lowercase() }
    }

    fun isLaunchable(pkg: String): Boolean {
        val cache = launchableCache ?: launchableApps().map { it.pkg }.toSet()
        if (pkg in cache) return true
        // Newly installed app: check directly.
        val found = pm.getLaunchIntentForPackage(pkg) != null
        if (found) launchableCache = cache + pkg
        return found
    }

    fun invalidate() { launchableCache = null }

    fun label(pkg: String): String = synchronized(labelCache) {
        labelCache.getOrPut(pkg) {
            runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })
        }
    }

    fun icon(pkg: String, sizePx: Int): ImageBitmap? {
        iconCache.get(pkg)?.let { return it }
        val drawable = runCatching { pm.getApplicationIcon(pkg) }.getOrNull() ?: return null
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bmp.asImageBitmap().also { iconCache.put(pkg, it) }
    }

    fun isInstalled(pkg: String): Boolean =
        runCatching { pm.getApplicationInfo(pkg, 0); true }.getOrElse { it !is PackageManager.NameNotFoundException && false }
}
