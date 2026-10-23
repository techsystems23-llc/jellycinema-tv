package org.jellyfin.androidtv.jellycinema

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Jelly Cinema TV updates itself (sideloaded builds only; the Play Store build is updated by the store).
 *
 * Where it looks, in order:
 *  1. The app's own update address, set when the app is built (`-Pjc.updateUrl=https://…/jellycinema-tv.json`,
 *     e.g. the latest GitHub release: https://github.com/<owner>/<repo>/releases/latest/download/jellycinema-tv.json).
 *  2. The Jellyfin server it is connected to, if the server owner put a build in the web folder's hub/ directory:
 *       <server>/web/hub/jellycinema-tv.json
 *       <server>/web/hub/jellycinema-tv.zip   (the APK, renamed: Jellyfin doesn't serve .apk files)
 *
 * The update information: {"versionCode":2,"versionName":"1.0.1","apk":"jellycinema-tv.zip","md5":"..."}.
 * "apk" is a file next to the .json, or a full https:// address. Checked when the app starts (at most every
 * 10 minutes) and from Settings. Nothing found = nothing happens.
 */
object UpdateChecker {
    private const val PREFS = "jellycinema_update"
    private const val KEY_LAST = "last_check"
    private const val MIN_INTERVAL_MS = 10 * 60 * 1000L   // every start, at most every 10 minutes
    private const val TIMEOUT_MS = 10_000

    /** What a check found, for the Settings button. */
    sealed interface Result {
        data object Skipped : Result
        data object NoServer : Result
        data object UpToDate : Result
        data class Installing(val versionName: String) : Result
        data object NeedsPermission : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun check(context: Context, serverUrl: String?, force: Boolean = false): Result {
        // Play Store builds are updated by the store only
        if (!BuildConfig.SELF_UPDATE) return Result.Skipped
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val feeds = buildList {
            BuildConfig.UPDATE_URL.takeIf { it.startsWith("https://") }?.let { add(it) }
            serverUrl?.takeIf { it.isNotBlank() }?.let { add(it.trimEnd('/') + "/web/hub/jellycinema-tv.json") }
        }
        if (feeds.isEmpty()) return Result.NoServer
        if (!force && now - prefs.getLong(KEY_LAST, 0) < MIN_INTERVAL_MS) return Result.Skipped
        prefs.edit().putLong(KEY_LAST, now).apply()
        return try {
            // cache-busting so the TV never reuses an old or half-finished copy
            val bust = "?t=$now"
            var feed = ""
            var json: String? = null
            for (f in feeds) {
                json = withContext(Dispatchers.IO) { runCatching { fetchText(f + bust) }.getOrNull() }
                if (json != null) { feed = f; break }
            }
            if (json == null) return Result.Failed(app.getString(R.string.jc_update_no_info))
            val base = feed.substringBeforeLast('/') + "/"
            val info = Json.parseToJsonElement(json).jsonObject
            val versionCode = info["versionCode"]?.jsonPrimitive?.int ?: return Result.Failed(app.getString(R.string.jc_update_unreadable))
            val versionName = info["versionName"]?.jsonPrimitive?.content ?: versionCode.toString()
            val apk = info["apk"]?.jsonPrimitive?.content ?: "jellycinema-tv.zip"
            val md5 = info["md5"]?.jsonPrimitive?.content
            if (versionCode <= BuildConfig.VERSION_CODE) return Result.UpToDate
            // a file next to the update information, or a full https:// address (e.g. a GitHub release asset)
            val apkUrl = when {
                apk.startsWith("https://") -> apk
                apk.contains('/') || apk.contains("..") || apk.contains(':') -> return Result.Failed(app.getString(R.string.jc_update_bad_name))
                else -> base + apk + bust
            }
            Timber.i("Jelly Cinema TV update %s (%d) available", versionName, versionCode)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
                // one-time: the TV has to allow this app to install its updates
                toast(app, app.getString(R.string.jc_update_allow_install, versionName))
                val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { app.startActivity(i) } catch (e: Exception) { Timber.w(e) }
                prefs.edit().putLong(KEY_LAST, 0).apply()
                return Result.NeedsPermission
            }
            val file = File(app.cacheDir, "jellycinema-tv-update.apk")
            file.delete()
            if (force) toast(app, app.getString(R.string.jc_update_downloading, versionName))
            val downloaded = withContext(Dispatchers.IO) { download(apkUrl, file) }
            if (!downloaded) { file.delete(); return Result.Failed(app.getString(R.string.jc_update_download_failed)) }
            val sum = withContext(Dispatchers.IO) { md5Of(file) }
            if (md5 != null && !md5.equals(sum, true)) {
                Timber.w("Update checksum mismatch %s != %s", sum, md5)
                file.delete()
                return Result.Failed(app.getString(R.string.jc_update_damaged))
            }
            toast(app, app.getString(R.string.jc_update_installing, versionName))
            withContext(Dispatchers.IO) { install(app, file) }
            Result.Installing(versionName)
        } catch (e: Exception) {
            Timber.w(e, "Update check failed")
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun fetchText(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = TIMEOUT_MS; c.readTimeout = TIMEOUT_MS
        c.useCaches = false
        return try {
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun download(url: String, out: File): Boolean {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = TIMEOUT_MS; c.readTimeout = 60_000
        c.useCaches = false
        return try {
            if (c.responseCode != 200) return false
            val expected = c.contentLengthLong
            c.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
            out.length() > 0 && (expected <= 0 || out.length() == expected)
        } finally { c.disconnect() }
    }

    private fun md5Of(f: File): String {
        val d = MessageDigest.getInstance("MD5")
        f.inputStream().use { s -> val b = ByteArray(65536); while (true) { val n = s.read(b); if (n < 0) break; d.update(b, 0, n) } }
        return d.digest().joinToString("") { "%02x".format(it) }
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("jellycinema-tv", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(context, id, Intent(context, UpdateReceiver::class.java), flags)
            session.commit(pending.intentSender)
        }
    }

    private suspend fun toast(context: Context, text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
}
