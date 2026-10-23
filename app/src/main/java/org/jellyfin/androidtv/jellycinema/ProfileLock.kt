package org.jellyfin.androidtv.jellycinema

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

/**
 * Optional 4-digit PIN per profile, kept on this TV only (Settings → Jelly Cinema → Profile PIN).
 * A profile with a PIN asks for it when the app starts on it or someone switches to it, so kids can't
 * open a parent's profile. Forgot the PIN? The profile's Jellyfin password removes it.
 */
object ProfileLock {
	private const val PREFS = "jellycinema_pins"
	const val LENGTH = 4

	/** The profile that was unlocked in this run of the app (switching to anyone else locks it again). */
	@Volatile private var unlocked: UUID? = null

	private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	private fun hash(userId: UUID, pin: String): String =
		MessageDigest.getInstance("SHA-256").digest("jellycinema:$userId:$pin".toByteArray())
			.joinToString("") { "%02x".format(it) }

	fun hasPin(context: Context, userId: UUID) = prefs(context).contains(userId.toString())

	fun setPin(context: Context, userId: UUID, pin: String) {
		prefs(context).edit().putString(userId.toString(), hash(userId, pin)).apply()
		unlocked = userId
	}

	fun removePin(context: Context, userId: UUID) {
		prefs(context).edit().remove(userId.toString()).apply()
	}

	fun check(context: Context, userId: UUID, pin: String): Boolean =
		prefs(context).getString(userId.toString(), null) == hash(userId, pin)

	/** Called whenever a profile becomes the signed-in one. True when its PIN must be entered first. */
	fun needsUnlock(context: Context, userId: UUID): Boolean {
		if (unlocked != userId) unlocked = null
		return hasPin(context, userId) && unlocked != userId
	}

	fun markUnlocked(userId: UUID) { unlocked = userId }
}
