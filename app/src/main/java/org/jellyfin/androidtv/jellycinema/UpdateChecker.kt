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
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Jelly Cinema TV updates itself from the Jellyfin server it is connected to.
 * The server owner drops a new build in the web folder's hub/ directory:
 *   <server>/web/hub/jellycinema-tv.json  {"versionCode":2,"versionName":"1.0.1","apk":"jellycinema-tv.zip","md5":"..."}
 *   <server>/web/hub/jellycinema-tv.zip   (the APK, renamed: Jellyfin doesn't serve .apk files)
 * Checked when the app starts (at most every 10 minutes). No channel on the server = nothing happens.
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
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (serverUrl.isNullOrBlank()) return Result.NoServer
        if (!force && now - prefs.getLong(KEY_LAST, 0) < MIN_INTERVAL_MS) return Result.Skipped
        prefs.edit().putLong(KEY_LAST, now).apply()
        return try {
            val base = serverUrl.trimEnd('/') + "/web/hub/"
            // cache-busting so the TV never reuses an old or half-finished copy
            val bust = "?t=$now"
            val json = withContext(Dispatchers.IO) { fetchText(base + "jellycinema-tv.json" + bust) }
                ?: return Result.Failed("No update information on the server")
            val info = Json.parseToJsonElement(json).jsonObject
            val versionCode = info["versionCode"]?.jsonPrimitive?.int ?: return Result.Failed("Update information is unreadable")
            val versionName = info["versionName"]?.jsonPrimitive?.content ?: versionCode.toString()
            val apk = info["apk"]?.jsonPrimitive?.content ?: "jellycinema-tv.zip"
            val md5 = info["md5"]?.jsonPrimitive?.content
            if (versionCode <= BuildConfig.VERSION_CODE) return Result.UpToDate
            if (apk.contains('/') || apk.contains("..")) return Result.Failed("Update file name is not allowed")
            Timber.i("Jelly Cinema TV update %s (%d) available", versionName, versionCode)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
                // one-time: the TV has to allow this app to install its updates
                toast(app, "Jelly Cinema $versionName is ready. Turn on \"Allow from this source\" for Jelly Cinema, then press Back and try again.")
                val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { app.startActivity(i) } catch (e: Exception) { Timber.w(e) }
                prefs.edit().putLong(KEY_LAST, 0).apply()
                return Result.NeedsPermission
            }
            val file = File(app.cacheDir, "jellycinema-tv-update.apk")
            file.delete()
            if (force) toast(app, "Downloading Jelly Cinema $versionName…")
            val downloaded = withContext(Dispatchers.IO) { download(base + apk + bust, file) }
            if (!downloaded) { file.delete(); return Result.Failed("Download didn't finish") }
            val sum = withContext(Dispatchers.IO) { md5Of(file) }
            if (md5 != null && !md5.equals(sum, true)) {
                Timber.w("Update checksum mismatch %s != %s", sum, md5)
                file.delete()
                return Result.Failed("Download was damaged, try again")
            }
            toast(app, "Updating Jelly Cinema to $versionName…")
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
