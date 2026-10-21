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
		if (items.isEmpty()) return
		val adapter = ItemRowAdapter(context, items, presenter ?: cardPresenter, rowsAdapter, QueryType.StaticItems)
		val row = ListRow(HeaderItem(title), adapter)
		adapter.setRow(row)
		adapter.Retrieve()
		rowsAdapter.add(row)
	}
}

/** The Jelly Cinema rows for this person: seasonal rows go first, the rest after Continue Watching / Next Up. */
class JellyCinemaHomeRows(val top10: List<HomeFragmentRow>, val seasonal: List<HomeFragmentRow>, val picks: List<HomeFragmentRow>)

suspend fun JellyCinemaRepository.loadHomeRows(user: UserDto): JellyCinemaHomeRows = coroutineScope {
	val cfg = config
	val seasonalJob = async { withTimeoutOrNull(15_000) { seasonal() }.orEmpty() }
	val picksJob = async { withTimeoutOrNull(15_000) { picks() }.orEmpty() }
	val topJob = async {
		if (!cfg.top10Enabled) return@async emptyList<BaseItemDto>() to emptyList()
		withTimeoutOrNull(15_000) { top10(user.id) } ?: (emptyList<BaseItemDto>() to emptyList())
	}

	val seasonalRows = seasonalJob.await().map { (row, items) -> JellyCinemaStaticRow(if (row.icon.isNotBlank()) "${row.icon}  ${row.title}" else row.title, items) }
	val (movies, shows) = topJob.await()
	val top10 = buildList {
		if (movies.size >= cfg.top10MinTitles) add(top10Row(cfg.top10MoviesTitle, movies))
		if (shows.size >= cfg.top10MinTitles) add(top10Row(cfg.top10ShowsTitle, shows))
	}
	val picks = picksJob.await()
	val picksRows = if (picks.isEmpty()) emptyList() else listOf(JellyCinemaStaticRow(cfg.picksTitle ?: "${cfg.brandName ?: "Jelly Cinema"} Picks", picks))
	JellyCinemaHomeRows(top10, seasonalRows, picksRows)
}

private fun top10Row(title: String, items: List<BaseItemDto>): HomeFragmentRow {
	val top = items.take(10)
	val ranks = top.mapIndexed { i, item -> item.id to i + 1 }.toMap()
	return JellyCinemaStaticRow(title, top, Top10CardPresenter(ranks))
}
