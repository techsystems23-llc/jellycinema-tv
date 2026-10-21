package org.jellyfin.androidtv.jellycinema

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import timber.log.Timber

/** Result of a self-update: if Android wants the person to confirm, show its confirmation screen. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let {
                    try { context.startActivity(it) } catch (e: Exception) { Timber.w(e) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Timber.i("Jelly Cinema TV updated")
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Timber.w("Jelly Cinema TV update failed: %d %s", status, msg)
                Toast.makeText(context, "Jelly Cinema update didn't install: ${msg ?: status}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
