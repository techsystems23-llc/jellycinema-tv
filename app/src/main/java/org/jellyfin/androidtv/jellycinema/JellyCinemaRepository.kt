package org.jellyfin.androidtv.jellycinema

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.displayPreferencesApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.DisplayPreferencesDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.ScrollDirection
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.UserDto
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.MonthDay
import java.util.UUID

/**
 * Jelly Cinema settings. Built-in defaults; if the server has the Jelly Cinema plugin, its settings page
 * (served as /JellyCinema/config.js) overrides them. Nothing here needs any server plugin.
 */
data class JellyCinemaConfig(
	val brandName: String? = null,
	val heroEnabled: Boolean = true,
	val heroMaxItems: Int = 15,
	val heroSlideSeconds: Int = 10,
	val heroTypes: Set<BaseItemKind> = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
	val heroLibraries: List<String> = emptyList(),
	val heroSkipWatched: Boolean = false,
	val heroTrailers: Boolean = true,
	val heroTrailerDelaySeconds: Int = 4,
	val trailerMaxSeconds: Int = 45,
	val followSelection: Boolean = true,
	val focusDelayMs: Long = 700,
	val previewTimeoutSeconds: Int = 20,
	val theaterEnabled: Boolean = true,
	val theaterIdleSeconds: Int = 90,
	val top10Enabled: Boolean = true,
	val top10Movies: Boolean = true,
	val top10MoviesTitle: String? = null,
	val top10Shows: Boolean = true,
	val top10ShowsTitle: String? = null,
	val top10MinTitles: Int = 3,
	val top10FillFromLibrary: Boolean = true,
	val top10RefreshDays: Int = 6,
	val picksEnabled: Boolean = true,
	val picksTitle: String? = null,
	val picksDays: Set<Int> = setOf(5, 6, 0),
	val picksCount: Int = 8,
	val picksRatings: List<String> = listOf("G", "PG", "TV-G", "TV-PG", "TV-Y", "TV-Y7"),
	val picksMinScore: Double = 6.5,
	val picksUnwatchedOnly: Boolean = true,
	val seasonsEnabled: Boolean = true,
	val seasonRows: List<SeasonRow> = SeasonRow.defaults,
	/** Holiday-heavy libraries get their own holiday row (see [JellyCinemaRepository.seasonal]). */
	val holidayLibraryRows: Boolean = true,
	/** ...when at least this share of the library's titles are holiday titles... */
	val holidayLibraryShare: Double = 0.2,
	/** ...and it has at least this many of them. */
	val holidayLibraryMin: Int = 8,
	val becauseRowCount: Int = 2,
	val genreRowCount: Int = 5,
	val collectionsEnabled: Boolean = true,
	val watchAgainEnabled: Boolean = true,
	val continueWatchingHideAfterDays: Int = 90,
	/** Kids' Home: bigger posters and no trailers (accounts with a rating limit, or set in Settings). */
	val kidsHome: Boolean = false,
	/** Lighter effects for low-memory TV boxes (no picture zoom, fewer falling leaves, 720p trailers). */
	val lightMode: Boolean = false,
)

/** A seasonal row: titles with any of [tags] between [from] and [to] (month-day, inclusive). */
data class SeasonRow(
	val key: String,
	val title: String,
	val icon: String,
	val tags: List<String>,
	val from: MonthDay,
	val to: MonthDay,
	val library: String? = null,
	val nameContains: String? = null,
	/** Also give each holiday-heavy library its own row ("<Library> Christmas"), see [JellyCinemaRepository.seasonal]. */
	val perLibrary: Boolean = false,
	/** Word in a title that also counts for the per-library rows (titles without the tag, e.g. TV movies). */
	val titleWord: String? = null,
	/** Title of a per-library row, with the library name as %1$s. */
	@androidx.annotation.StringRes val libraryTitle: Int = 0,
) {
	fun isActive(today: LocalDate = LocalDate.now()): Boolean {
		val md = MonthDay.from(today)
		return if (!from.isAfter(to)) !md.isBefore(from) && !md.isAfter(to)
		else !md.isBefore(from) || !md.isAfter(to)
	}

	companion object {
		private fun thanksgiving(year: Int): MonthDay {
			var d = LocalDate.of(year, 11, 1)
			while (d.dayOfWeek != java.time.DayOfWeek.THURSDAY) d = d.plusDays(1)
			return MonthDay.from(d.plusWeeks(3))
		}

		private fun easterSunday(year: Int): LocalDate {
			val a = year % 19; val b = year / 100; val c = year % 100; val d = b / 4; val e = b % 4
			val f = (b + 8) / 25; val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
			val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
			val month = (h + l - 7 * m + 114) / 31; val day = ((h + l - 7 * m + 114) % 31) + 1
			return LocalDate.of(year, month, day)
		}

		private val year get() = LocalDate.now().year
		val defaults
			get() = listOf(
				SeasonRow("easter", JellyCinemaSettings.text(org.jellyfin.androidtv.R.string.jc_row_easter), "🐣", listOf("easter", "easter bunny", "easter egg", "jesus christ", "crucifixion of jesus"),
					MonthDay.from(easterSunday(year).minusDays(21)), MonthDay.from(easterSunday(year).plusDays(1))),
				SeasonRow("halloween", JellyCinemaSettings.text(org.jellyfin.androidtv.R.string.jc_row_halloween), "🎃", listOf("halloween"), MonthDay.of(10, 1), MonthDay.of(10, 31)),
				SeasonRow("christmas", JellyCinemaSettings.text(org.jellyfin.androidtv.R.string.jc_row_christmas), "🎄", listOf("christmas", "santa claus", "christmas eve", "christmas tree"),
					thanksgiving(year), MonthDay.of(12, 26), perLibrary = true, titleWord = "Christmas",
					libraryTitle = org.jellyfin.androidtv.R.string.jc_row_library_christmas),
			)
	}
}

class JellyCinemaRepository(private val api: ApiClient) {
	companion object {
		val heroFields = setOf(
			ItemFields.OVERVIEW, ItemFields.GENRES, ItemFields.TAGLINES, ItemFields.REMOTE_TRAILERS,
			ItemFields.CHILD_COUNT, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.DATE_CREATED,
		)
		val cardFields = setOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.OVERVIEW, ItemFields.GENRES, ItemFields.CHILD_COUNT,
			ItemFields.TAGLINES, ItemFields.REMOTE_TRAILERS)
		private const val TOP10_KEY = "jc-top10"
		private const val CLIENT = "jellycinema"
	}

	@Volatile
	var config: JellyCinemaConfig = JellyCinemaConfig()
		private set

	@Volatile
	private var configLoadedAt = 0L

	/** Loads the settings once every few minutes; returns the current ones otherwise. */
	@Volatile
	private var configToken: String? = null

	suspend fun ensureConfig(): JellyCinemaConfig {
		// reload when the settings changed, every few minutes, and when someone else signs in (Kids' Home)
		if (JellyCinemaSettings.changed || api.accessToken != configToken || System.currentTimeMillis() - configLoadedAt > 5 * 60_000L) {
			configToken = api.accessToken
			JellyCinemaSettings.changed = false
			loadConfig()
			configLoadedAt = System.currentTimeMillis()
		}
		return config
	}

	/** Reads the Jelly Cinema plugin's settings if the server has the plugin; keeps the built-in defaults otherwise. */
	suspend fun loadConfig(): JellyCinemaConfig = withContext(Dispatchers.IO) {
		val base = api.baseUrl?.trimEnd('/') ?: return@withContext config
		config = try {
			val c = URL("$base/JellyCinema/config.js").openConnection() as HttpURLConnection
			c.connectTimeout = 5000; c.readTimeout = 5000
			val text = try {
				if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
			} finally {
				c.disconnect()
			}
			if (text == null) JellyCinemaConfig() else parseConfig(text)
		} catch (e: Exception) {
			Timber.i("Jelly Cinema: no server settings (%s), using built-in defaults", e.message)
			JellyCinemaConfig()
		}
		config = JellyCinemaSettings.applyTo(config)
		// Kids' Home: automatic for accounts with a rating limit
		val limited = runCatching { api.userApi.getCurrentUser().content.policy?.maxParentalRating != null }.getOrDefault(false)
		config = JellyCinemaSettings.applyHomeStyle(config, limited)
		config
	}

	private fun parseConfig(js: String): JellyCinemaConfig {
		val start = js.indexOf('{'); val end = js.lastIndexOf('}')
		if (start < 0 || end <= start) return JellyCinemaConfig()
		val root = Json { isLenient = true; ignoreUnknownKeys = true }.parseToJsonElement(js.substring(start, end + 1)).jsonObject
		fun JsonObject.obj(k: String) = this[k] as? JsonObject
		fun JsonElement?.s() = (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
		fun JsonElement?.b() = (this as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull
		fun JsonElement?.i() = (this as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull
		fun JsonElement?.d() = (this as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull
		val d = JellyCinemaConfig()
		val brand = root.obj("brand"); val banner = root.obj("banner"); val hero = banner?.obj("hero")
		val theater = root.obj("theaterMode"); val top = root.obj("top10"); val picks = root.obj("picks")
		val seasons = root.obj("seasons") ?: root.obj("seasonal")
		val types = hero?.get("types").s()?.split(',')?.mapNotNull {
			when (it.trim()) { "Movie" -> BaseItemKind.MOVIE; "Series" -> BaseItemKind.SERIES; else -> null }
		}?.toSet()?.takeIf { it.isNotEmpty() }
		return d.copy(
			brandName = brand?.get("name").s(),
			heroEnabled = hero?.get("enabled").b() ?: d.heroEnabled,
			heroMaxItems = hero?.get("maxItems").i() ?: d.heroMaxItems,
			heroSlideSeconds = hero?.get("slideSeconds").i() ?: d.heroSlideSeconds,
			heroTypes = types ?: d.heroTypes,
			heroLibraries = hero?.get("libraries").s()?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: d.heroLibraries,
			heroSkipWatched = hero?.get("skipWatched").b() ?: d.heroSkipWatched,
			heroTrailers = hero?.get("trailers").b() ?: d.heroTrailers,
			heroTrailerDelaySeconds = hero?.get("trailerDelaySeconds").i() ?: d.heroTrailerDelaySeconds,
			trailerMaxSeconds = banner?.get("trailerMaxSeconds").i() ?: d.trailerMaxSeconds,
			followSelection = banner?.get("followSelection").b() ?: d.followSelection,
			focusDelayMs = banner?.get("focusDelayMs").i()?.toLong() ?: d.focusDelayMs,
			previewTimeoutSeconds = banner?.get("previewTimeoutSeconds").i() ?: d.previewTimeoutSeconds,
			theaterEnabled = theater?.get("enabled").b() ?: d.theaterEnabled,
			theaterIdleSeconds = theater?.get("idleSeconds").i() ?: d.theaterIdleSeconds,
			top10Enabled = top?.get("enabled").b() ?: d.top10Enabled,
			top10Movies = top?.get("movies").b() ?: d.top10Movies,
			top10MoviesTitle = top?.get("moviesTitle").s() ?: d.top10MoviesTitle,
			top10Shows = top?.get("shows").b() ?: d.top10Shows,
			top10ShowsTitle = top?.get("showsTitle").s() ?: d.top10ShowsTitle,
			top10MinTitles = top?.get("minTitles").i() ?: d.top10MinTitles,
			top10FillFromLibrary = top?.get("fillFromLibrary").b() ?: d.top10FillFromLibrary,
			top10RefreshDays = top?.get("refreshDays").i() ?: d.top10RefreshDays,
			picksEnabled = picks?.get("enabled").b() ?: d.picksEnabled,
			picksTitle = picks?.get("title").s()?.takeIf { it.isNotBlank() },
			picksDays = picks?.get("days")?.let { e ->
				runCatching { e.jsonArray.mapNotNull { it.i() }.toSet() }.getOrNull()
					?: e.s()?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet()
			} ?: d.picksDays,
			picksCount = picks?.get("count").i() ?: d.picksCount,
			picksRatings = picks?.get("ratings")?.let { e ->
				runCatching { e.jsonArray.mapNotNull { it.s() } }.getOrNull() ?: e.s()?.split(',')?.map { it.trim() }
			} ?: d.picksRatings,
			picksMinScore = picks?.get("minScore").d() ?: d.picksMinScore,
			picksUnwatchedOnly = picks?.get("unwatchedOnly").b() ?: d.picksUnwatchedOnly,
			seasonsEnabled = seasons?.get("enabled").b() ?: d.seasonsEnabled,
			continueWatchingHideAfterDays = root.obj("continueWatching")?.get("hideAfterDays").i() ?: d.continueWatchingHideAfterDays,
		)
	}

	/** Look every title up again as the signed-in user: Jellyfin answers 404 for anything they may not open. */
	// Items are always fetched as the signed-in user, so Jellyfin already leaves out anything they may not open
	// (parental ratings, library access). No extra per-title lookups: those made Home slow.
	@Suppress("RedundantSuspendModifier")
	suspend fun verify(items: List<BaseItemDto>): List<BaseItemDto> = items

	private suspend fun libraryIds(names: List<String>): List<UUID> {
		if (names.isEmpty()) return emptyList()
		val wanted = names.map { it.trim().lowercase() }
		val views = runCatching { api.userViewsApi.getUserViews().content.items }.getOrDefault(emptyList())
		return views.filter { (it.name ?: "").trim().lowercase() in wanted }.map { it.id }
	}

	/** Banner titles: random titles with a backdrop, from what this person may watch. */
	suspend fun heroItems(): List<BaseItemDto> = withContext(Dispatchers.IO) { coroutineScope {
		val cfg = config
		// a long rotation (at least 50) of titles that have a trailer, so nearly every slide plays one
		val count = maxOf(cfg.heroMaxItems, 50)
		val parents: List<UUID?> = libraryIds(cfg.heroLibraries).ifEmpty { listOf(null) }
		suspend fun fetch(withTrailer: Boolean, limit: Int) = parents.map { parent ->
			async {
				runCatching {
					api.itemsApi.getItems(
						includeItemTypes = cfg.heroTypes,
						recursive = true,
						sortBy = listOf(ItemSortBy.RANDOM),
						limit = limit,
						imageTypes = listOf(ImageType.BACKDROP),
						hasTrailer = if (withTrailer) true else null,
						fields = heroFields,
						enableUserData = true,
						parentId = parent,
						filters = if (cfg.heroSkipWatched) listOf(ItemFilter.IS_UNPLAYED) else null,
					).content.items
				}.getOrDefault(emptyList())
			}
		}.awaitAll().flatten()
		var items = fetch(true, count + 20).filter { !it.backdropImageTags.isNullOrEmpty() }.distinctBy { it.id }
		// a library with few trailers: fill up with other titles
		if (items.size < count) items = (items + fetch(false, count).filter { !it.backdropImageTags.isNullOrEmpty() }).distinctBy { it.id }
		items.shuffled().take(count)
	} }

	// ---------------------------------------------------------------------------------------------
	// Top 10: the ranking is shared with the Jelly Cinema web scripts (DisplayPreferences "jc-top10").
	// Any admin signing in to the app refreshes it from Jellyfin's own watch data (no plugin needed)
	// and saves it on every account; each person then sees only the ranked titles they may open.

	private suspend fun readRanking(userId: UUID): Map<String, String> = runCatching {
		api.displayPreferencesApi.getDisplayPreferences(TOP10_KEY, userId, CLIENT).content.customPrefs?.mapNotNull { (k, v) -> v?.let { k to it } }?.toMap() ?: emptyMap()
	}.getOrDefault(emptyMap())

	private suspend fun rankFromWatchData(users: List<UserDto>): Pair<List<UUID>, List<UUID>> = coroutineScope {
		val per = users.map { u ->
			async(Dispatchers.IO) {
				u.id to runCatching {
					api.itemsApi.getItems(
						userId = u.id, recursive = true, isPlayed = true,
						includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
						sortBy = listOf(ItemSortBy.DATE_PLAYED),
						sortOrder = listOf(SortOrder.DESCENDING), limit = 1000, enableImages = false, enableUserData = true,
					).content.items
				}.getOrDefault(emptyList())
			}
		}.awaitAll()
		val now = java.time.OffsetDateTime.now()
		fun rank(days: Long, type: BaseItemKind): List<UUID> {
			val users = HashMap<UUID, MutableSet<UUID>>(); val plays = HashMap<UUID, Int>()
			for ((uid, items) in per) for (it in items) {
				if (it.type != type) continue
				val last = it.userData?.lastPlayedDate
				if (days > 0 && (last == null || last.isBefore(now.minusDays(days).toLocalDateTime()))) continue
				val key = (if (type == BaseItemKind.EPISODE) it.seriesId else it.id) ?: continue
				users.getOrPut(key) { mutableSetOf() }.add(uid)
				plays[key] = (plays[key] ?: 0) + (it.userData?.playCount ?: 1).coerceAtLeast(1)
			}
			return users.keys.sortedWith(compareByDescending<UUID> { users[it]!!.size }.thenByDescending { plays[it] ?: 0 })
		}
		fun merge(vararg l: List<UUID>) = l.flatMap { it }.distinct()
		merge(rank(7, BaseItemKind.MOVIE), rank(30, BaseItemKind.MOVIE), rank(0, BaseItemKind.MOVIE)).take(60) to
			merge(rank(7, BaseItemKind.EPISODE), rank(30, BaseItemKind.EPISODE), rank(0, BaseItemKind.EPISODE)).take(60)
	}

	/** Admins only: refresh the shared ranking when it is missing or older than top10RefreshDays. */
	suspend fun refreshRankingIfNeeded(me: UserDto, force: Boolean = false) = withContext(Dispatchers.IO) {
		if (me.policy?.isAdministrator != true) return@withContext
		val mine = readRanking(me.id)
		val updated = mine["updated"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
		if (!force && updated != null && updated.plusDays(config.top10RefreshDays.toLong()).isAfter(LocalDate.now())) return@withContext
		val users = runCatching { api.userApi.getUsers().content }.getOrDefault(emptyList())
		if (users.isEmpty()) return@withContext
		val (movies, shows) = rankFromWatchData(users)
		val prefs = mapOf("movies" to movies.joinToString(",") { it.toString().replace("-", "") },
			"shows" to shows.joinToString(",") { it.toString().replace("-", "") },
			"updated" to LocalDate.now().toString())
		for (u in users) runCatching {
			api.displayPreferencesApi.updateDisplayPreferences(TOP10_KEY, u.id, CLIENT, DisplayPreferencesDto(
				id = TOP10_KEY, sortBy = "SortName", rememberIndexing = false, primaryImageHeight = 0, primaryImageWidth = 0,
				customPrefs = prefs, scrollDirection = ScrollDirection.HORIZONTAL, showBackdrop = false, rememberSorting = false,
				sortOrder = SortOrder.ASCENDING, showSidebar = false, client = CLIENT,
			))
		}
		Timber.i("Jelly Cinema Top 10 refreshed: %d movies, %d shows (saved for %d accounts)", movies.size, shows.size, users.size)
	}

	private fun parseIds(csv: String?): List<UUID> = csv?.split(',')?.mapNotNull { s ->
		val h = s.trim().replace("-", "")
		if (h.length != 32) null else runCatching {
			UUID.fromString("${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}")
		}.getOrNull()
	} ?: emptyList()

	/** This person's Top 10 movies and shows, in ranked order, only titles they may open. */
	suspend fun top10(userId: UUID): Pair<List<BaseItemDto>, List<BaseItemDto>> = withContext(Dispatchers.IO) {
		val cfg = config
		val p = readRanking(userId)
		suspend fun resolve(ids: List<UUID>, type: BaseItemKind): List<BaseItemDto> {
			val out = ArrayList<BaseItemDto>()
			for (chunk in ids.chunked(20)) {
				if (out.size >= 10) break
				val found = runCatching {
					api.itemsApi.getItems(ids = chunk, fields = cardFields, enableUserData = true).content.items
				}.getOrDefault(emptyList()).associateBy { it.id }
				val ordered = chunk.mapNotNull { found[it] }.filter { it.type == type }
				// Jellyfin answers a look-up by id even for titles in libraries this person may not use (it only
				// applies the rating limit). The ranking is shared by everyone, so check each title the way its
				// details page would: only what this person can open stays. (Found by the privacy check.)
				val allowed = coroutineScope {
					ordered.map { item ->
						async { item.takeIf { runCatching { api.userLibraryApi.getItem(itemId = item.id) }.isSuccess } }
					}.awaitAll().filterNotNull()
				}
				out.addAll(allowed)
			}
			var list = out.take(10)
			if (cfg.top10FillFromLibrary && list.size < 10) {
				val have = list.map { it.id }.toSet()
				val extra = runCatching {
					api.itemsApi.getItems(
						includeItemTypes = setOf(type), recursive = true,
						sortBy = listOf(ItemSortBy.COMMUNITY_RATING, ItemSortBy.SORT_NAME), sortOrder = listOf(SortOrder.DESCENDING),
						imageTypes = listOf(ImageType.PRIMARY), limit = 40, fields = cardFields, enableUserData = true,
					).content.items
				}.getOrDefault(emptyList()).filter { it.id !in have }
				list = list + verify(extra.take(10 - list.size + 4)).take(10 - list.size)
			}
			return list
		}
		val movies = if (cfg.top10Movies) resolve(parseIds(p["movies"]), BaseItemKind.MOVIE) else emptyList()
		val shows = if (cfg.top10Shows) resolve(parseIds(p["shows"]), BaseItemKind.SERIES) else emptyList()
		movies to shows
	}

	/** "<brand> Picks": well-rated family titles this person hasn't watched, on the chosen days. */
	suspend fun picks(today: LocalDate = LocalDate.now()): List<BaseItemDto> = withContext(Dispatchers.IO) {
		val cfg = config
		val dow = today.dayOfWeek.value % 7   // 0 = Sunday, like the web scripts
		if (!cfg.picksEnabled || dow !in cfg.picksDays) return@withContext emptyList()
		val list = runCatching {
			api.itemsApi.getItems(
				includeItemTypes = setOf(BaseItemKind.MOVIE), recursive = true, sortBy = listOf(ItemSortBy.RANDOM),
				officialRatings = cfg.picksRatings, minCommunityRating = cfg.picksMinScore, limit = cfg.picksCount + 8,
				filters = if (cfg.picksUnwatchedOnly) listOf(ItemFilter.IS_UNPLAYED) else null,
				imageTypes = listOf(ImageType.PRIMARY), fields = cardFields, enableUserData = true,
			).content.items
		}.getOrDefault(emptyList())
		verify(list.take(cfg.picksCount + 4)).take(cfg.picksCount)
	}

	/** The seasonal rows that are on today, each with titles this person may open. */
	suspend fun seasonal(today: LocalDate = SeasonalPreference.rowsDate(JellyCinemaSettings.context)): List<Pair<SeasonRow, List<BaseItemDto>>> = withContext(Dispatchers.IO) {
		val cfg = config
		if (!cfg.seasonsEnabled) return@withContext emptyList()
		cfg.seasonRows.filter { it.isActive(today) }.mapNotNull { row ->
			val parent = row.library?.let { libraryIds(listOf(it)).firstOrNull() ?: return@mapNotNull null }
			val list = runCatching {
				api.itemsApi.getItems(
					includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES), recursive = true,
					tags = row.tags, parentId = parent, sortBy = listOf(ItemSortBy.RANDOM), limit = 40,
					searchTerm = row.nameContains, imageTypes = listOf(ImageType.PRIMARY), fields = cardFields, enableUserData = true,
				).content.items
			}.getOrDefault(emptyList())
			val ok = verify(list)
			if (ok.size < 3) null else row to ok
		}.flatMap { (row, items) -> listOf(row to items) + if (row.perLibrary && cfg.holidayLibraryRows) libraryHolidayRows(row) else emptyList() }
	}

	/**
	 * Extra holiday rows for libraries that are mostly holiday titles: a library where at least
	 * [JellyCinemaConfig.holidayLibraryShare] of the titles are tagged for the holiday (or have its word in the
	 * title) and that has at least [JellyCinemaConfig.holidayLibraryMin] of them gets its own "<Library name> Christmas" row next to the main one.
	 * Nothing is named in the code: it works from each server's own libraries (a library full of
	 * Christmas TV movies gets "<its name> Christmas"; an ordinary Movies library doesn't).
	 */
	private suspend fun libraryHolidayRows(row: SeasonRow): List<Pair<SeasonRow, List<BaseItemDto>>> = coroutineScope {
		val minTitles = config.holidayLibraryMin
		val share = config.holidayLibraryShare
		val types = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
		val views = runCatching { api.userViewsApi.getUserViews().content.items }.getOrDefault(emptyList())
			.filter { it.collectionType == null || it.collectionType in setOf(CollectionType.MOVIES, CollectionType.TVSHOWS, CollectionType.HOMEVIDEOS) }
		views.map { lib ->
			async {
				runCatching {
					val total = api.itemsApi.getItems(parentId = lib.id, includeItemTypes = types, recursive = true, limit = 0,
						enableTotalRecordCount = true).content.totalRecordCount
					if (total < minTitles) return@runCatching null
					val tagged = api.itemsApi.getItems(parentId = lib.id, includeItemTypes = types, recursive = true, tags = row.tags,
						sortBy = listOf(ItemSortBy.RANDOM), limit = 40, imageTypes = listOf(ImageType.PRIMARY), fields = cardFields,
						enableUserData = true, enableTotalRecordCount = true).content
					val named = row.titleWord?.let {
						api.itemsApi.getItems(parentId = lib.id, includeItemTypes = types, recursive = true, searchTerm = it,
							sortBy = listOf(ItemSortBy.RANDOM), limit = 40, imageTypes = listOf(ImageType.PRIMARY), fields = cardFields,
							enableUserData = true, enableTotalRecordCount = true).content
					}
					val matches = maxOf(tagged.totalRecordCount, named?.totalRecordCount ?: 0)
					if (matches < minTitles || matches < total * share) return@runCatching null
					val items = (tagged.items + named?.items.orEmpty()).distinctBy { it.id }.shuffled().take(40)
					val name = lib.name?.trim().orEmpty()
					row.copy(key = "${row.key}-${lib.id}", title = JellyCinemaSettings.text(row.libraryTitle, name), icon = "❄️", library = name) to items
				}.getOrNull()
			}
		}.awaitAll().filterNotNull()
	}

	// ---------------------------------------------------------------------------------------------
	// "Because You Watched" and genre rows, made by the app from this person's own watch history
	// (the server's Home Screen Sections plugin is not needed).

	/** Up to [count] "Because You Watched <title>" rows from the most recently finished movies and shows. */
	suspend fun becauseYouWatched(count: Int = 2): List<Pair<String, List<BaseItemDto>>> = withContext(Dispatchers.IO) { coroutineScope {
		val recent = runCatching {
			api.itemsApi.getItems(
				includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE), recursive = true,
				filters = listOf(ItemFilter.IS_PLAYED), sortBy = listOf(ItemSortBy.DATE_PLAYED),
				sortOrder = listOf(SortOrder.DESCENDING), limit = 30, enableUserData = true,
			).content.items
		}.getOrDefault(emptyList())
		// one entry per movie or show (episodes count as their show)
		val seeds = recent.mapNotNull { i ->
			if (i.type == BaseItemKind.EPISODE) i.seriesId?.let { it to (i.seriesName ?: "") } else i.id to (i.name ?: "")
		}.distinctBy { it.first }.take(count + 2)
		// all lookups at once (faster), then keep the first [count] that have enough titles
		seeds.map { (id, name) ->
			async {
				val similar = runCatching {
					api.libraryApi.getSimilarItems(
						org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest(itemId = id, fields = cardFields, limit = 20)
					).content.items
				}.getOrDefault(emptyList()).filter { it.userData?.played != true }
				if (similar.size >= 4 && name.isNotBlank()) JellyCinemaSettings.text(org.jellyfin.androidtv.R.string.jc_row_because_you_watched, name) to similar else null
			}
		}.awaitAll().filterNotNull().take(count)
	} }

	/** Movie rows for the genres this person watches most, e.g. "Science Fiction Movies". */
	suspend fun genreRows(count: Int = 4): List<Pair<String, List<BaseItemDto>>> = withContext(Dispatchers.IO) { coroutineScope {
		val played = runCatching {
			api.itemsApi.getItems(
				includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE), recursive = true,
				filters = listOf(ItemFilter.IS_PLAYED), sortBy = listOf(ItemSortBy.DATE_PLAYED), sortOrder = listOf(SortOrder.DESCENDING),
				fields = setOf(ItemFields.GENRES), limit = 300,
			).content.items
		}.getOrDefault(emptyList())
		val counts = HashMap<String, Int>()
		played.forEach { i -> i.genres.orEmpty().forEach { g -> counts[g] = (counts[g] ?: 0) + 1 } }
		var genres = counts.entries.sortedByDescending { it.value }.map { it.key }
		if (genres.size < count) {
			// new viewer: use the library's biggest movie genres
			val all = runCatching {
				api.genresApi.getGenres(includeItemTypes = setOf(BaseItemKind.MOVIE), sortBy = listOf(ItemSortBy.SORT_NAME), limit = 40).content.items
			}.getOrDefault(emptyList()).mapNotNull { it.name }
			genres = (genres + all.shuffled()).distinct()
		}
		// a few more genres than needed, all at once; keep the first [count] (in order) with enough movies
		genres.take(count + 3).map { g ->
			async {
				val list = runCatching {
					api.itemsApi.getItems(
						includeItemTypes = setOf(BaseItemKind.MOVIE), recursive = true, genres = listOf(g),
						sortBy = listOf(ItemSortBy.RANDOM), limit = 24, imageTypes = listOf(ImageType.PRIMARY),
						fields = cardFields, enableUserData = true,
					).content.items
				}.getOrDefault(emptyList())
				// unwatched first
				val sorted = list.sortedBy { if (it.userData?.played == true) 1 else 0 }
				if (sorted.size >= 6) JellyCinemaSettings.text(org.jellyfin.androidtv.R.string.jc_row_genre_movies, g) to sorted else null
			}
		}.awaitAll().filterNotNull().take(count)
	} }

	/** The person's collections (box sets), only ones they may open. */
	suspend fun collections(): List<BaseItemDto> = withContext(Dispatchers.IO) {
		runCatching {
			api.itemsApi.getItems(
				includeItemTypes = setOf(BaseItemKind.BOX_SET), recursive = true, sortBy = listOf(ItemSortBy.RANDOM),
				limit = 24, imageTypes = listOf(ImageType.PRIMARY), fields = cardFields, enableUserData = true,
			).content.items
		}.getOrDefault(emptyList())
	}

	/** Movies and shows this person finished, most recent first. */
	suspend fun watchAgain(): List<BaseItemDto> = withContext(Dispatchers.IO) {
		runCatching {
			api.itemsApi.getItems(
				includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES), recursive = true,
				filters = listOf(ItemFilter.IS_PLAYED), sortBy = listOf(ItemSortBy.DATE_PLAYED), sortOrder = listOf(SortOrder.DESCENDING),
				limit = 24, imageTypes = listOf(ImageType.PRIMARY), fields = cardFields, enableUserData = true,
			).content.items
		}.getOrDefault(emptyList())
	}
}
