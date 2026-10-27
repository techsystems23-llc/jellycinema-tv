package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.focusGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.jellycinema.HeroBackdrop
import org.jellyfin.androidtv.jellycinema.HeroInfo
import org.jellyfin.androidtv.jellycinema.JellyCinemaHeroState
import org.jellyfin.androidtv.jellycinema.JellyCinemaRepository
import org.jellyfin.androidtv.jellycinema.UpdateChecker
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.android.ext.android.inject

class HomeFragment : Fragment() {
	private val api by inject<ApiClient>()
	private val jellyCinema by inject<JellyCinemaRepository>()
	private val heroState by inject<JellyCinemaHeroState>()
	private val backgroundService by inject<org.jellyfin.androidtv.data.service.BackgroundService>()
	private val userRepository by inject<org.jellyfin.androidtv.auth.repository.UserRepository>()
	private val sessionRepository by inject<SessionRepository>()
	private val serverRepository by inject<ServerRepository>()
	private val notificationRepository by inject<NotificationsRepository>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		val rowsFocusRequester = remember { FocusRequester() }
		val playFocusRequester = remember { FocusRequester() }
		val slides by heroState.slides.collectAsState()
		val index by heroState.index.collectAsState()
		val preview by heroState.preview.collectAsState()
		val selected by heroState.selected.collectAsState()
		val theater by heroState.theater.collectAsState()
		val trailerPlaying by heroState.trailerPlaying.collectAsState()
		val trailerPending by heroState.trailerPending.collectAsState()
		val cfg = jellyCinema.config
		val showHero = cfg.heroEnabled && slides.isNotEmpty()
		val chromeAlpha by animateFloatAsState(if (theater) 0f else 1f, tween(900), label = "theater")
		// while browsing the rows the banner shrinks so the rows and their titles have room
		var rowsFocused by remember { mutableStateOf(false) }
		// the banner only shrinks once the person is really browsing the rows (not the start-up focus)
		val keys by heroState.keys.collectAsState()
		val browsing = rowsFocused && keys > 0
		val heroHeight by androidx.compose.animation.core.animateDpAsState(
			when { theater -> 400.dp; browsing -> 140.dp; else -> 244.dp }, tween(350), label = "hero-height")

		// First focus: the remote works straight away (rows get focus at once); when the banner's titles
		// arrive, Play takes over, unless the person has already started pressing buttons.
		val openedAt = remember { System.currentTimeMillis() }
		var playFocused by remember { mutableStateOf(false) }
		LaunchedEffect(Unit) {
			if (!heroState.slides.value.isNotEmpty()) runCatching { rowsFocusRequester.requestFocus() }
		}
		LaunchedEffect(showHero) {
			if (!showHero || playFocused) return@LaunchedEffect
			// try for a moment while the banner lays out; stop if the person starts pressing buttons
			repeat(20) {
				if (heroState.lastKey > openedAt) return@LaunchedEffect
				withFrameNanos { }
				if (runCatching { playFocusRequester.requestFocus() }.isSuccess && !rowsFocused) { playFocused = true; return@LaunchedEffect }
				delay(100)
			}
		}

		// slideshow: next title every few seconds (waits while a trailer plays or a card is shown)
		// (while a trailer is still loading it waits up to 25 s for it, so trailers get the chance to play)
		val slideStart = remember(index, preview) { System.currentTimeMillis() }
		LaunchedEffect(index, preview, trailerPlaying, trailerPending, slides.size) {
			if (preview != null || trailerPlaying || slides.size < 2) return@LaunchedEffect
			val wait = if (trailerPending && cfg.heroTrailers) 25_000L else cfg.heroSlideSeconds * 1000L
			delay((wait - (System.currentTimeMillis() - slideStart)).coerceAtLeast(1_500L))
			withFrameNanos { } // waits while the app is in the background (no frames are drawn then)
			heroState.next()
		}

		// banner follows the highlighted card after a short pause, then goes back to the slideshow
		LaunchedEffect(selected) {
			val item = selected
			if (item == null || !cfg.followSelection) return@LaunchedEffect
			delay(cfg.focusDelayMs)
			heroState.preview.value = item
			delay(cfg.previewTimeoutSeconds * 1000L)
			heroState.preview.value = null
		}

		// Theater Mode after a while without a key press
		LaunchedEffect(cfg.theaterEnabled, showHero) {
			if (!cfg.theaterEnabled || !showHero) return@LaunchedEffect
			while (true) {
				delay(5000)
				if (System.currentTimeMillis() - heroState.lastInput > cfg.theaterIdleSeconds * 1000L) heroState.enterTheater()
			}
		}

		Box(Modifier.fillMaxSize()) {
			if (showHero) HeroBackdrop(heroState)

			Column {
				Box(Modifier.alpha(chromeAlpha)) { MainToolbar(MainToolbarActiveButton.Home) }

				if (showHero) HeroInfo(
					state = heroState,
					playFocusRequester = playFocusRequester,
					modifier = Modifier.fillMaxWidth().height(heroHeight),
					compact = browsing,
					onBannerFocused = {
						heroState.selected.value = null
						heroState.preview.value = null
					},
				)

				// The leanback code has its own awful focus handling that doesn't work properly with Compose view inteop to workaround this
				// issue we add custom behavior that only allows focus exit when the current selected row is the first one. Additionally when
				// we do switch the focus, we reset the leanback state so it won't cause weird behavior when focus is regained
				var rowsSupportFragment by remember { mutableStateOf<HomeRowsFragment?>(null) }
				AndroidFragment<HomeRowsFragment>(
					modifier = Modifier
						.alpha(chromeAlpha)
						.onFocusChanged { rowsFocused = it.hasFocus }
						.focusGroup()
						.focusRequester(rowsFocusRequester)
						.focusProperties {
							onExit = {
								// moving focus from code (e.g. to the banner's Play button) is always allowed
								val isFirstRowSelected = rowsSupportFragment?.selectedPosition?.let { it <= 0 } ?: false
								if (requestedFocusDirection == FocusDirection.Enter) {
									// allowed
								} else if (requestedFocusDirection != FocusDirection.Up || !isFirstRowSelected) {
									cancelFocusChange()
								} else {
									rowsSupportFragment?.selectedPosition = 0
									rowsSupportFragment?.verticalGridView?.clearFocus()
								}
							}
						}
						.fillMaxWidth()
						.weight(1f),
					onUpdate = { fragment ->
						rowsSupportFragment = fragment
					}
				)
			}

			// seasonal falling leaves, bats, snow... over the whole screen
			// Jelly Cinema logo, top right: under the clock, and up in the corner in full screen (Theater Mode)
			if (showHero) {
				val logoTop by androidx.compose.animation.core.animateDpAsState(if (theater) 31.dp else 58.dp, tween(900), label = "logo-top")
				androidx.compose.foundation.Image(
					painter = androidx.compose.ui.res.painterResource(org.jellyfin.androidtv.R.drawable.app_logo),
					contentDescription = null,
					modifier = Modifier
						.align(androidx.compose.ui.Alignment.TopEnd)
						.padding(end = 48.dp, top = logoTop)
						.height(34.dp),
				)
			}
			org.jellyfin.androidtv.jellycinema.SeasonalEffects()
		}
	}

	override fun onResume() {
		super.onResume()
		heroState.homeVisible = true
		// no leftover picture (e.g. the sign-in screen's) behind Home
		backgroundService.clearBackgrounds()
		heroState.onInput(fromKey = false)
	}

	override fun onPause() {
		super.onPause()
		heroState.homeVisible = false
		heroState.selected.value = null
		heroState.preview.value = null
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		sessionRepository.currentSession
			.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
			.map { session ->
				if (session == null) null
				else serverRepository.getServer(session.serverId)
			}
			.onEach { server ->
				notificationRepository.updateServerNotifications(server)
			}
			.launchIn(viewLifecycleOwner.lifecycleScope)

		// Jelly Cinema banner titles (only what this person may watch) and app updates from the server
		viewLifecycleOwner.lifecycleScope.launch {
			val cfg = jellyCinema.ensureConfig()
			if (cfg.heroEnabled && heroState.slides.value.isEmpty()) {
				val items = runCatching { jellyCinema.heroItems() }.onFailure { Timber.w(it, "Banner titles failed") }.getOrDefault(emptyList())
				org.jellyfin.androidtv.jellycinema.TrailerMemory.load(requireContext())
				// the banner appears once the start-up intro has played (drawing it then doesn't make the intro stutter)
				org.jellyfin.androidtv.jellycinema.JellyCinemaIntroState.awaitAnimation()
				heroState.setSlides(org.jellyfin.androidtv.jellycinema.TrailerMemory.playableFirst(items))
			}
			// after the start-up intro, so it doesn't compete with it
			while (org.jellyfin.androidtv.jellycinema.JellyCinemaIntroState.showing) kotlinx.coroutines.delay(200)
			UpdateChecker.check(requireContext(), api.baseUrl)
			// admins keep the shared Top 10 ranking fresh, in the background (shows next time Home opens)
			userRepository.currentUser.value?.takeIf { it.policy?.isAdministrator == true }?.let { me ->
				runCatching { jellyCinema.refreshRankingIfNeeded(me) }.onFailure { Timber.w(it, "Top 10 refresh failed") }
			}
		}
	}
}
