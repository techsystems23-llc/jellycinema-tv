package org.jellyfin.androidtv.jellycinema

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** Small pieces of Jelly Cinema state shown in Settings, so problems on a TV can be read off the screen. */
object JellyCinemaDiagnostics {
	val trailer = MutableStateFlow("No trailer tried yet")

	fun trailer(message: String) {
		android.util.Log.i("JellyCinemaTrailer", message)
		trailer.value = message
	}
}

/** Seasonal effects choice: "" = automatic by date, "off", or a theme name to preview it. */
object SeasonalPreference {
	private const val PREFS = "jellycinema"
	private const val KEY = "season_effects"
	val choices = listOf("", "off", "newyear", "winter", "valentines", "stpatricks", "spring", "easter", "summer",
		"july4", "fall", "halloween", "thanksgiving", "christmas")

	fun get(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
	fun set(context: Context, value: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()

	fun theme(context: Context): String = when (val v = get(context)) {
		"" -> SeasonalTheme.current()
		"off" -> "plain"
		else -> v
	}

	fun label(value: String): String = when (value) {
		"" -> "Automatic (today: ${name(SeasonalTheme.current())})"
		"off" -> "Off"
		else -> "Preview: ${name(value)}"
	}

	fun name(theme: String) = when (theme) {
		"newyear" -> "New Year sparkles"; "winter" -> "Winter snow"; "valentines" -> "Valentine's hearts"
		"stpatricks" -> "St. Patrick's clovers"; "spring" -> "Spring petals"; "easter" -> "Easter eggs & petals"
		"summer" -> "Summer fireflies"; "july4" -> "4th of July stars"; "fall" -> "Fall leaves"
		"halloween" -> "Halloween leaves & bats"; "thanksgiving" -> "Thanksgiving leaves"; "christmas" -> "Christmas snow"
		else -> "None today"
	}
}
