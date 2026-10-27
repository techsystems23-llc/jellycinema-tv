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

	/**
	 * The date the holiday rows follow. Automatic: today. When a holiday's effects are chosen by hand
	 * (Settings → Jelly Cinema → Seasonal effects), its rows show too, so people can preview a season.
	 */
	fun rowsDate(context: Context?, today: java.time.LocalDate = java.time.LocalDate.now()): java.time.LocalDate {
		val y = today.year
		return when (context?.let { get(it) }.orEmpty()) {
			"newyear" -> java.time.LocalDate.of(y, 1, 1)
			"valentines" -> java.time.LocalDate.of(y, 2, 10)
			"stpatricks" -> java.time.LocalDate.of(y, 3, 14)
			"july4" -> java.time.LocalDate.of(y, 7, 2)
			"halloween" -> java.time.LocalDate.of(y, 10, 25)
			"thanksgiving" -> java.time.LocalDate.of(y, 11, 20)
			"christmas" -> java.time.LocalDate.of(y, 12, 15)
			else -> today
		}
	}

	fun theme(context: Context): String = when (val v = get(context)) {
		"" -> SeasonalTheme.current()
		"off" -> "plain"
		else -> v
	}

	fun label(value: String): String = when (value) {
		"" -> JellyCinemaSettings.text(org.jellyfin.androidtv.R.string.jc_season_automatic, name(SeasonalTheme.current()))
		"off" -> JellyCinemaSettings.text(org.jellyfin.androidtv.R.string.jc_setting_off)
		else -> JellyCinemaSettings.text(org.jellyfin.androidtv.R.string.jc_season_preview, name(value))
	}

	fun name(theme: String) = JellyCinemaSettings.text(when (theme) {
		"newyear" -> org.jellyfin.androidtv.R.string.jc_season_newyear; "winter" -> org.jellyfin.androidtv.R.string.jc_season_winter
		"valentines" -> org.jellyfin.androidtv.R.string.jc_season_valentines; "stpatricks" -> org.jellyfin.androidtv.R.string.jc_season_stpatricks
		"spring" -> org.jellyfin.androidtv.R.string.jc_season_spring; "easter" -> org.jellyfin.androidtv.R.string.jc_season_easter
		"summer" -> org.jellyfin.androidtv.R.string.jc_season_summer; "july4" -> org.jellyfin.androidtv.R.string.jc_season_july4
		"fall" -> org.jellyfin.androidtv.R.string.jc_season_fall; "halloween" -> org.jellyfin.androidtv.R.string.jc_season_halloween
		"thanksgiving" -> org.jellyfin.androidtv.R.string.jc_season_thanksgiving; "christmas" -> org.jellyfin.androidtv.R.string.jc_season_christmas
		else -> org.jellyfin.androidtv.R.string.jc_season_none
	})
}
