package org.jellyfin.androidtv.jellycinema

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.ui.home.HomeFragmentRow
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.UserDto
import timber.log.Timber

/** A Home row with a fixed list of titles (Jelly Cinema rows). */
class JellyCinemaStaticRow(
	private val title: String,
	private val items: List<BaseItemDto>,
	private val presenter: Presenter? = null,
) : HomeFragmentRow {
	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		addAt(context, cardPresenter, rowsAdapter, rowsAdapter.size())
	}

	/** Adds the row at [index]; returns true when a row was added. */
	fun addAt(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>, index: Int): Boolean {
		if (items.isEmpty()) return false
		val adapter = ItemRowAdapter(context, items, presenter ?: cardPresenter, rowsAdapter, QueryType.StaticItems)
		val row = ListRow(HeaderItem(title), adapter)
		adapter.setRow(row)
		adapter.Retrieve()
		rowsAdapter.add(index.coerceIn(0, rowsAdapter.size()), row)
		return true
	}
}

/** The rows shown above Jellyfin's own rows: Top 10, seasonal, Picks (quick to load). */
suspend fun JellyCinemaRepository.loadTopRows(user: UserDto): List<JellyCinemaStaticRow> = coroutineScope {
	val cfg = config
	val seasonalJob = async { withTimeoutOrNull(10_000) { seasonal() }.orEmpty() }
	val picksJob = async { withTimeoutOrNull(10_000) { picks() }.orEmpty() }
	val topJob = async {
		if (!cfg.top10Enabled) return@async emptyList<BaseItemDto>() to emptyList()
		withTimeoutOrNull(10_000) { top10(user.id) } ?: (emptyList<BaseItemDto>() to emptyList())
	}
	val (movies, shows) = topJob.await()
	buildList {
		if (movies.size >= cfg.top10MinTitles) add(top10Row(JellyCinemaSettings.title(cfg.top10MoviesTitle, "Top 10 Movies This Week", org.jellyfin.androidtv.R.string.jc_row_top10_movies), movies))
		if (shows.size >= cfg.top10MinTitles) add(top10Row(JellyCinemaSettings.title(cfg.top10ShowsTitle, "Top 10 Shows This Week", org.jellyfin.androidtv.R.string.jc_row_top10_shows), shows))
		seasonalJob.await().forEach { (row, items) ->
			add(JellyCinemaStaticRow(if (row.icon.isNotBlank()) "${row.icon}  ${row.title}" else row.title, items))
		}
		val picks = picksJob.await()
		if (picks.isNotEmpty()) add(JellyCinemaStaticRow(cfg.picksTitle ?: JellyCinemaSettings.text(org.jellyfin.androidtv.R.string.jc_row_picks, cfg.brandName ?: "Jelly Cinema"), picks))
	}
}

/** The rows after Recently Added: Because You Watched, genres, Collections, Watch Again. */
suspend fun JellyCinemaRepository.loadDiscoverRows(user: UserDto): List<JellyCinemaStaticRow> = coroutineScope {
	val cfg = config
	val becauseJob = async { if (cfg.becauseRowCount <= 0) emptyList() else withTimeoutOrNull(12_000) { becauseYouWatched(cfg.becauseRowCount) }.orEmpty() }
	val genreJob = async { if (cfg.genreRowCount <= 0) emptyList() else withTimeoutOrNull(12_000) { genreRows(cfg.genreRowCount) }.orEmpty() }
	// Collections: hidden for accounts with a rating limit (kids saw grown-up collection names on the web)
	val collectionsJob = async { if (!cfg.collectionsEnabled || user.policy?.maxParentalRating != null) emptyList() else withTimeoutOrNull(12_000) { collections() }.orEmpty() }
	val watchAgainJob = async { if (!cfg.watchAgainEnabled) emptyList() else withTimeoutOrNull(12_000) { watchAgain() }.orEmpty() }
	buildList {
		becauseJob.await().forEach { (t, items) -> add(JellyCinemaStaticRow(t, items)) }
		genreJob.await().forEach { (t, items) -> add(JellyCinemaStaticRow(t, items)) }
		collectionsJob.await().takeIf { it.size >= 3 }?.let { add(JellyCinemaStaticRow(JellyCinemaSettings.text(org.jellyfin.androidtv.R.string.jc_row_collections), it)) }
		watchAgainJob.await().takeIf { it.size >= 3 }?.let { add(JellyCinemaStaticRow(JellyCinemaSettings.text(org.jellyfin.androidtv.R.string.jc_row_watch_again), it)) }
	}
}

private fun top10Row(title: String, items: List<BaseItemDto>): JellyCinemaStaticRow {
	val top = items.take(10)
	val ranks = top.mapIndexed { i, item -> item.id to i + 1 }.toMap()
	return JellyCinemaStaticRow(title, top, Top10CardPresenter(ranks))
}
