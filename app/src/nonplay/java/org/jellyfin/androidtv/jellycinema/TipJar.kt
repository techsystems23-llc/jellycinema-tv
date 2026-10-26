package org.jellyfin.androidtv.jellycinema

import android.app.Activity
import android.content.Context

/**
 * Builds outside Google Play: no store payments. "Support the developer" shows a QR code for the page
 * set with -Pjc.supportUrl instead (see SettingsSupportScreen), or is hidden when none is set.
 */
object TipJar {
	const val usesStore = false

	class Tip(val id: String, val name: String, val price: String)

	enum class Result { Thanks, Pending, Cancelled, Failed }

	@Suppress("UNUSED_PARAMETER")
	suspend fun tips(context: Context): List<Tip> = emptyList()

	@Suppress("UNUSED_PARAMETER")
	fun buy(activity: Activity, tip: Tip, onResult: (Result) -> Unit) = onResult(Result.Failed)
}
