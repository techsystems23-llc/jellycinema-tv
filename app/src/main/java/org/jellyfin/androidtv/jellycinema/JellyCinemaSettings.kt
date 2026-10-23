package org.jellyfin.androidtv.jellycinema

import android.content.Context
import androidx.annotation.StringRes
import org.jellyfin.androidtv.R

/**
 * Jelly Cinema options people can change on the TV itself (Settings → Jelly Cinema).
 * Each option starts on "Automatic": the server's Jelly Cinema plugin settings if it has the plugin,
 * otherwise the app's built-in default. Choosing a value overrides that on this TV.
 */
object JellyCinemaSettings {
	private const val PREFS = "jellycinema_settings"

	/** One choice of an option: the stored value ("" = automatic) and its label. */
	data class Choice(val value: String, @StringRes val label: Int = 0, val text: String? = null)

	enum class Option(
		val key: String,
		@StringRes val title: Int,
		@StringRes val description: Int,
		val choices: List<Choice>,
	) {
		TRAILERS("trailers", R.string.jc_setting_trailers, R.string.jc_setting_trailers_desc, onOff()),
		SLIDE_SECONDS("slide_seconds", R.string.jc_setting_slide_seconds, R.string.jc_setting_slide_seconds_desc,
			auto() + listOf(6, 8, 10, 15, 20, 30).map { Choice("$it", text = "$it s") }),
		THEATER("theater", R.string.jc_setting_theater, R.string.jc_setting_theater_desc,
			auto() + Choice("off", R.string.jc_setting_off) + listOf(30, 60, 90, 120, 300).map { Choice("$it", text = if (it < 60) "$it s" else "${it / 60} min" + if (it % 60 != 0) " ${it % 60} s" else "") }),
		TOP10("top10", R.string.jc_setting_top10, R.string.jc_setting_top10_desc, onOff()),
		PICKS("picks", R.string.jc_setting_picks, R.string.jc_setting_picks_desc, onOff()),
		SEASONAL_ROWS("seasonal_rows", R.string.jc_setting_seasonal_rows, R.string.jc_setting_seasonal_rows_desc, onOff()),
		HOLIDAY_LIBRARY_ROWS("holiday_library_rows", R.string.jc_setting_holiday_library_rows, R.string.jc_setting_holiday_library_rows_desc,
			auto() + Choice("off", R.string.jc_setting_off) + listOf(10, 20, 35, 50).map { Choice("$it", text = "$it%") }),
		BECAUSE_ROWS("because_rows", R.string.jc_setting_because_rows, R.string.jc_setting_because_rows_desc,
			auto() + (0..4).map { Choice("$it", text = "$it") }),
		GENRE_ROWS("genre_rows", R.string.jc_setting_genre_rows, R.string.jc_setting_genre_rows_desc,
			auto() + (0..8).map { Choice("$it", text = "$it") }),
		COLLECTIONS("collections", R.string.jc_setting_collections, R.string.jc_setting_collections_desc, onOff()),
		WATCH_AGAIN("watch_again", R.string.jc_setting_watch_again, R.string.jc_setting_watch_again_desc, onOff()),
		KIDS_HOME("kids_home", R.string.jc_setting_kids_home, R.string.jc_setting_kids_home_desc, onOff()),
		PERFORMANCE("performance", R.string.jc_setting_performance, R.string.jc_setting_performance_desc,
			auto() + listOf(Choice("full", R.string.jc_setting_performance_full), Choice("light", R.string.jc_setting_performance_light))),
	}

	private fun auto() = listOf(Choice("", R.string.jc_setting_automatic))
	private fun onOff() = auto() + listOf(Choice("on", R.string.jc_setting_on), Choice("off", R.string.jc_setting_off))

	@Volatile private var appContext: Context? = null

	fun init(context: Context) { appContext = context.applicationContext }

	/** Text from the app's translation files (follows the TV's language). */
	fun text(@StringRes id: Int, vararg args: Any): String = appContext?.getString(id, *args) ?: ""

	/** A title from the server's plugin settings, unless it is just the plugin's English default (then the translation). */
	fun title(fromServer: String?, englishDefault: String, @StringRes id: Int): String =
		fromServer?.takeIf { it.isNotBlank() && it != englishDefault } ?: text(id)

	private fun prefs(context: Context? = appContext) = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	fun get(option: Option, context: Context? = appContext): String = prefs(context)?.getString(option.key, "") ?: ""

	fun set(context: Context, option: Option, value: String) {
		prefs(context)?.edit()?.putString(option.key, value)?.apply()
		changed = true
	}

	/** Set when an option changed; Home reloads its settings the next time it opens. */
	@Volatile var changed = false

	fun label(context: Context, option: Option, value: String = get(option, context)): String {
		val c = option.choices.firstOrNull { it.value == value } ?: option.choices.first()
		return c.text ?: context.getString(c.label)
	}

	fun next(option: Option, value: String): String {
		val i = option.choices.indexOfFirst { it.value == value }
		return option.choices[(i + 1).mod(option.choices.size)].value
	}

	/** A TV box with little memory (most cheap streaming sticks): effects are toned down automatically. */
	fun lowEndDevice(context: Context? = appContext): Boolean {
		val am = context?.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
		val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
		return am.isLowRamDevice || info.totalMem in 1 until 2_600L * 1024 * 1024
	}

	/** Kids' Home (automatic for accounts with a rating limit) and the lighter mode for low-memory boxes. */
	fun applyHomeStyle(cfg: JellyCinemaConfig, ratingLimited: Boolean): JellyCinemaConfig {
		val kids = when (get(Option.KIDS_HOME)) { "on" -> true; "off" -> false; else -> ratingLimited }
		val light = when (get(Option.PERFORMANCE)) { "light" -> true; "full" -> false; else -> lowEndDevice() }
		// kids: no trailers unless banner trailers were turned on by hand
		val trailers = if (kids && get(Option.TRAILERS) != "on") false else cfg.heroTrailers
		return cfg.copy(kidsHome = kids, lightMode = light, heroTrailers = trailers)
	}

	/** The server's / built-in settings with this TV's own choices on top. */
	fun applyTo(cfg: JellyCinemaConfig): JellyCinemaConfig {
		fun v(o: Option) = get(o).takeIf { it.isNotEmpty() }
		fun b(o: Option, d: Boolean) = when (v(o)) { "on" -> true; "off" -> false; else -> d }
		fun i(o: Option, d: Int) = v(o)?.toIntOrNull() ?: d
		val theater = v(Option.THEATER)
		val holiday = v(Option.HOLIDAY_LIBRARY_ROWS)
		return cfg.copy(
			heroTrailers = b(Option.TRAILERS, cfg.heroTrailers),
			heroSlideSeconds = i(Option.SLIDE_SECONDS, cfg.heroSlideSeconds),
			theaterEnabled = if (theater == "off") false else if (theater != null) true else cfg.theaterEnabled,
			theaterIdleSeconds = theater?.toIntOrNull() ?: cfg.theaterIdleSeconds,
			top10Enabled = b(Option.TOP10, cfg.top10Enabled),
			picksEnabled = b(Option.PICKS, cfg.picksEnabled),
			seasonsEnabled = b(Option.SEASONAL_ROWS, cfg.seasonsEnabled),
			holidayLibraryRows = if (holiday == "off") false else if (holiday != null) true else cfg.holidayLibraryRows,
			holidayLibraryShare = holiday?.toIntOrNull()?.let { it / 100.0 } ?: cfg.holidayLibraryShare,
			becauseRowCount = i(Option.BECAUSE_ROWS, cfg.becauseRowCount),
			genreRowCount = i(Option.GENRE_ROWS, cfg.genreRowCount),
			collectionsEnabled = b(Option.COLLECTIONS, cfg.collectionsEnabled),
			watchAgainEnabled = b(Option.WATCH_AGAIN, cfg.watchAgainEnabled),
		)
	}
}
