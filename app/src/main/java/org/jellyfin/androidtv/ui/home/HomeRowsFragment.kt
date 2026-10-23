package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.CustomMessage
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.constant.LiveTvOption
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.CustomMessageRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.jellycinema.JellyCinemaHeroState
import org.jellyfin.androidtv.jellycinema.JellyCinemaRepository
import org.jellyfin.androidtv.jellycinema.loadDiscoverRows
import org.jellyfin.androidtv.jellycinema.loadTopRows
import kotlinx.coroutines.async
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.browsing.CompositeClickedListener
import org.jellyfin.androidtv.ui.browsing.CompositeSelectedListener
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.itemhandling.refreshItem
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.AudioEventListener
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.androidtv.util.KeyProcessor
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

class HomeRowsFragment : RowsSupportFragment(), AudioEventListener, View.OnKeyListener {
	private val api by inject<ApiClient>()
	private val backgroundService by inject<BackgroundService>()
	private val playbackManager by inject<PlaybackManager>()
	private val mediaManager by inject<MediaManager>()
	private val notificationsRepository by inject<NotificationsRepository>()
	private val userRepository by inject<UserRepository>()
	private val userPreferences by inject<UserPreferences>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val userViewsRepository by inject<UserViewsRepository>()
	private val dataRefreshService by inject<DataRefreshService>()
	private val customMessageRepository by inject<CustomMessageRepository>()
	private val navigationRepository by inject<NavigationRepository>()
	private val itemLauncher by inject<ItemLauncher>()
	private val keyProcessor by inject<KeyProcessor>()
	private val jellyCinema by inject<JellyCinemaRepository>()
	private val heroState by inject<JellyCinemaHeroState>()

	private val helper by lazy { HomeFragmentHelper(requireContext(), userRepository, userPreferences) }

	// Data
	private var currentItem: BaseRowItem? = null
	private var currentRow: ListRow? = null
	private var justLoaded = true

	// Special rows
	private val notificationsRow by lazy { NotificationsHomeFragmentRow(lifecycleScope, notificationsRepository) }
	private val nowPlaying by lazy { HomeFragmentNowPlayingRow(lifecycleScope, playbackManager, mediaManager) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		adapter = MutableObjectAdapter<Row>(PositionableListRowPresenter())

		lifecycleScope.launch(Dispatchers.IO) {
			val currentUser = withTimeout(30.seconds) {
				userRepository.currentUser.filterNotNull().first()
			}

			// Jelly Cinema rows load in the background (not holding up Jellyfin's own rows); they slot in
			// when ready: Top 10 / seasonal / Picks at the top, the discover rows after Recently Added.
			runCatching { jellyCinema.ensureConfig() }
			val topJob = async { runCatching { jellyCinema.loadTopRows(currentUser) }.onFailure { Timber.w(it, "Jelly Cinema top rows failed") }.getOrDefault(emptyList()) }
			val discoverJob = async { runCatching { jellyCinema.loadDiscoverRows(currentUser) }.onFailure { Timber.w(it, "Jelly Cinema rows failed") }.getOrDefault(emptyList()) }

			// Start out with default sections
			val homesections = userSettingPreferences.activeHomesections

			// Check for coroutine cancellation
			if (!isActive) return@launch

			// The same rows, in the same order, as the Jelly Cinema home page on the web
			// (made by the app itself, so no Home Screen Sections plugin is needed):
			// My Media, Continue Watching, Next Up, Recently Added Movies/Shows, then (added later)
			// Because You Watched, genre rows, Collections, Watch Again.
			val rows = mutableListOf<HomeFragmentRow>()
			val views = userViewsRepository.views.first()
			rows.add(HomeFragmentViewsRow(small = false))
			rows.add(helper.loadResumeVideo())
			rows.add(helper.loadNextUp())
			rows.add(helper.loadRecentlyAdded(views))
			val extraRows = mutableListOf<HomeFragmentRow>()
			if (HomeSectionType.RESUME_AUDIO in homesections) extraRows.add(helper.loadResumeAudio())
			if (HomeSectionType.LIVE_TV in homesections && currentUser.policy?.enableLiveTvAccess == true) {
				extraRows.add(HomeFragmentLiveTVRow(requireActivity(), userRepository))
				extraRows.add(helper.loadOnNow())
			}

			@Suppress("UNCHECKED_CAST")
			val rowsAdapter = adapter as MutableObjectAdapter<Row>
			val cardPresenter = CardPresenter()
			var topIndex = 0

			// Top 10 / Halloween / Picks usually arrive within a second or so: give them a moment, so they can
			// simply go first. If they take longer, Jellyfin's rows show first and they slot in above.
			val earlyTop = withTimeoutOrNull(1_500) { topJob.await() }
			var topAdded = false

			// The start-up intro plays first; building the rows is heavy and would make it stutter
			org.jellyfin.androidtv.jellycinema.JellyCinemaIntroState.awaitAnimation()

			// Add sections to layout
			withContext(Dispatchers.Main) {
				notificationsRow.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
				nowPlaying.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
				topIndex = rowsAdapter.size()
				earlyTop?.let { list -> list.forEach { it.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter) }; topAdded = true }
				for (row in rows) row.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
				for (row in extraRows) row.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)

				// Wire up Live TV sibling rows so the On Now row removes the buttons row when empty
				for (i in 0 until rowsAdapter.size()) {
					val listRow = rowsAdapter.get(i) as? ListRow ?: continue
					val itemAdapter = listRow.adapter as? ItemRowAdapter ?: continue
					if (itemAdapter.queryType == QueryType.LiveTvProgram && i > 0) {
						val previousRow = rowsAdapter.get(i - 1)
						if (previousRow != null) itemAdapter.setSiblingRow(previousRow)
					}
				}
				verticalGridView?.post { org.jellyfin.androidtv.jellycinema.JellyCinemaIntroState.homeRowsShown.value = true }
					?: run { org.jellyfin.androidtv.jellycinema.JellyCinemaIntroState.homeRowsShown.value = true }
			}

			// Top 10, Halloween, Picks: first on the page (if they weren't ready in time above)
			val top = if (topAdded) emptyList() else topJob.await()
			if (isActive && top.isNotEmpty()) withContext(Dispatchers.Main) {
				val keep = selectedPosition
				var at = topIndex
				for (row in top) if (row.addAt(requireContext(), cardPresenter, rowsAdapter, at)) at++
				// Rows added above the current one make the list scroll down to keep the old row in place,
				// which would hide Top 10 above the banner. Show the page from the top again unless the person
				// is already browsing further down.
				if (at > topIndex) {
					if (keep > topIndex) setSelectedPosition(keep + (at - topIndex), false)
					else {
						// after the list has taken the new rows in (next layout pass), go back to the top
						verticalGridView?.post {
							setSelectedPosition(0, false)
							verticalGridView?.scrollToPosition(0)
						}
					}
				}
			}

			// Because You Watched, genres, Collections, Watch Again: after Jellyfin's rows
			val discover = discoverJob.await()
			if (isActive) withContext(Dispatchers.Main) {
				val at = (rowsAdapter.size() - extraRowsInAdapter(rowsAdapter, extraRows.size)).coerceAtLeast(topIndex)
				var i = at
				for (row in discover) if (row.addAt(requireContext(), cardPresenter, rowsAdapter, i)) i++
			}
		}

		onItemViewClickedListener = CompositeClickedListener().apply {
			registerListener(ItemViewClickedListener())
			registerListener(notificationsRow::onItemClicked)
		}

		onItemViewSelectedListener = CompositeSelectedListener().apply {
			registerListener(ItemViewSelectedListener())
		}

		customMessageRepository.message
			.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
			.onEach { message ->
				when (message) {
					CustomMessage.RefreshCurrentItem -> refreshCurrentItem()
					else -> Unit
				}
			}.launchIn(lifecycleScope)

		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				api.webSocket.subscribe<UserDataChangedMessage>()
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)

				api.webSocket.subscribe<LibraryChangedMessage>()
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)
			}
		}

		// Subscribe to Audio messages
		mediaManager.addAudioEventListener(this)
	}

	/** Audio / Live TV rows sit at the very end; the discover rows go just before them. */
	private fun extraRowsInAdapter(rowsAdapter: MutableObjectAdapter<Row>, count: Int): Int =
		if (count == 0) 0 else count.coerceAtMost(rowsAdapter.size())

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		// Jelly Cinema: keep the highlighted row (with its title) near the top of the rows area
		setAlignment((56 * resources.displayMetrics.density).toInt())
	}

	override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
		if (event?.action != KeyEvent.ACTION_UP) return false
		return keyProcessor.handleKey(keyCode, currentItem, activity)
	}

	override fun onResume() {
		super.onResume()

		//React to deletion
		if (currentRow != null && currentItem != null && currentItem?.baseItem != null && currentItem!!.baseItem!!.id == dataRefreshService.lastDeletedItemId) {
			(currentRow!!.adapter as ItemRowAdapter).remove(currentItem)
			currentItem = null
			dataRefreshService.lastDeletedItemId = null
		}

		if (!justLoaded) {
			//Re-retrieve anything that needs it but delay slightly so we don't take away gui landing
			refreshCurrentItem()
			refreshRows()
		} else {
			justLoaded = false
		}

		// Update audio queue
		Timber.i("Updating audio queue in HomeFragment (onResume)")
		nowPlaying.update(requireContext(), adapter as MutableObjectAdapter<Row>)
	}

	override fun onQueueStatusChanged(hasQueue: Boolean) {
		if (activity == null || requireActivity().isFinishing) return

		Timber.i("Updating audio queue in HomeFragment (onQueueStatusChanged)")
		nowPlaying.update(requireContext(), adapter as MutableObjectAdapter<Row>)
	}

	private fun refreshRows(force: Boolean = false, delayed: Boolean = true) {
		lifecycleScope.launch(Dispatchers.IO) {
			if (delayed) delay(1.5.seconds)

			repeat(adapter.size()) { i ->
				val rowAdapter = (adapter[i] as? ListRow)?.adapter as? ItemRowAdapter
				if (force) rowAdapter?.Retrieve()
				else rowAdapter?.ReRetrieveIfNeeded()
			}
		}
	}

	private fun refreshCurrentItem() {
		val adapter = currentRow?.adapter as? ItemRowAdapter ?: return
		val item = currentItem ?: return

		Timber.i("Refresh item ${item.getFullName(requireContext())}")
		adapter.refreshItem(api, this, item)
	}

	override fun onDestroy() {
		super.onDestroy()

		mediaManager.removeAudioEventListener(this)
	}

	private inner class ItemViewClickedListener : OnItemViewClickedListener {
		override fun onItemClicked(
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row?,
		) {
			if (item is GridButton) {
				when (item.id) {
					LiveTvOption.LIVE_TV_GUIDE_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvGuide)
					LiveTvOption.LIVE_TV_SCHEDULE_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvSchedule)
					LiveTvOption.LIVE_TV_RECORDINGS_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvRecordings)
					LiveTvOption.LIVE_TV_SERIES_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvSeriesRecordings)
				}
			}

			if (item !is BaseRowItem) return
			if (row !is ListRow) return
			@Suppress("UNCHECKED_CAST")
			itemLauncher.launch(item, row.adapter as MutableObjectAdapter<Any>, requireContext())
		}
	}

	private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
		override fun onItemSelected(
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row?,
		) {
			if (item !is BaseRowItem) {
				currentItem = null
				heroState.selected.value = null
			} else {
				currentItem = item
				currentRow = row as ListRow

				val itemRowAdapter = row.adapter as? ItemRowAdapter
				itemRowAdapter?.loadMoreItemsIfNeeded(itemRowAdapter.indexOf(item))

				// the Jelly Cinema banner shows the highlighted title
				heroState.selected.value = item.baseItem
			}
		}
	}
}
