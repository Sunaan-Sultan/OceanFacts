package com.pixel.oceanfacts

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * The outward-facing actions behind the You tab's settings rows — Play listing, share sheet,
 * support mail and a manual update check.
 *
 * Every entry point degrades gracefully: a device with no Play Store, no browser or no mail app
 * gets a fallback (or a toast) rather than an ActivityNotFoundException.
 */
object AppActions {

    const val SUPPORT_EMAIL = "sunaansultan5@gmail.com"

    private const val TAG = "OceanActions"

    private fun playUrl(context: Context) =
        "https://play.google.com/store/apps/details?id=${context.packageName}"

    /** The installed build's versionCode, or 0 if the package cannot be read. */
    fun versionCode(context: Context): Long = packageInfo(context)?.let { info ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    } ?: 0L

    /**
     * "1.0 (1)", read off the *installed* package rather than BuildConfig so it always describes
     * the APK actually running.
     */
    fun versionLabel(context: Context): String {
        val info = packageInfo(context) ?: return "—"
        return "${info.versionName} (${versionCode(context)})"
    }

    private fun packageInfo(context: Context): PackageInfo? = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
    }.getOrElse {
        Log.w(TAG, "Could not read package info: ${it.message}")
        null
    }

    /**
     * The store listing — also the "leave a review" destination. Play's in-app review API is
     * deliberately not used here: it silently draws nothing once the per-user quota is spent
     * (and on non-Play installs), so an explicit tap would appear dead. Google's own guidance
     * says the same: do not put that API behind a rate button.
     *
     * Opens the Play app if installed, otherwise the web listing in a browser.
     */
    fun openPlayListing(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(context, market)) return
        val web = Intent(Intent.ACTION_VIEW, playUrl(context).toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!start(context, web)) toast(context, "No app available to open the Play Store.")
    }

    fun shareApp(context: Context) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Ocean Facts")
            putExtra(
                Intent.EXTRA_TEXT,
                "Ocean Facts — a daily descent into the sea.\n${playUrl(context)}",
            )
        }
        val chooser = Intent.createChooser(send, "Share Ocean Facts")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!start(context, chooser)) toast(context, "No app available to share with.")
    }

    /**
     * Opens a support draft pre-filled with the version and device details that would otherwise
     * have to be asked for in a reply. Falls back to copying the address.
     */
    fun emailSupport(context: Context) {
        val subject = "Ocean Facts support"
        val body = buildString {
            append("\n\n———\n")
            append("App: Ocean Facts ${versionLabel(context)}\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        }
        // ACTION_SENDTO with a mailto: URI resolves only to mail apps, so the chooser never
        // offers unrelated share targets.
        //
        // Gmail's external-compose activity reads the subject and body from the mailto: query
        // and ignores EXTRA_SUBJECT/EXTRA_TEXT, so the query is what actually fills the draft.
        // The extras stay for clients that only read those.
        val uri = "mailto:$SUPPORT_EMAIL" +
            "?subject=${Uri.encode(subject)}" +
            "&body=${Uri.encode(body)}"
        val mail = Intent(Intent.ACTION_SENDTO, uri.toUri()).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (start(context, mail)) return
        copyToClipboard(context, SUPPORT_EMAIL)
        toast(context, "No mail app found — address copied to clipboard.")
    }

    /**
     * Asks Play whether a newer build is live and reports back through [onResult] so the caller
     * can show the answer inline. An available update sends the user to the listing; the
     * forced-update gate in [MainActivity] owns the blocking flow.
     */
    fun checkForUpdates(context: Context, onResult: (String) -> Unit) {
        AppUpdateManagerFactory.create(context).appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    onResult("Update available — opening Play Store")
                    openPlayListing(context)
                } else {
                    onResult("You are on the latest version")
                }
            }
            .addOnFailureListener { e ->
                // Sideloaded, offline, or no Play Store: there is no answer to give.
                Log.w(TAG, "Update check failed: ${e.message}")
                onResult("Could not reach the Play Store")
            }
    }

    fun openExternal(context: Context, url: String) {
        val uri = url.toUri()
        val tab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .intent
            .setData(uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(context, tab)) return
        val view = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!start(context, view)) toast(context, "No browser available to open this page.")
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Support email", text))
    }

    private fun start(context: Context, intent: Intent): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity for ${intent.action}: ${e.message}")
            false
        }

    private fun toast(context: Context, message: String) =
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
