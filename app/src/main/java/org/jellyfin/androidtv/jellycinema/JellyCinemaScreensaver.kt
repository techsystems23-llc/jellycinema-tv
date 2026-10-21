package org.jellyfin.androidtv.jellycinema

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.koin.compose.koinInject
import timber.log.Timber

/**
 * Jelly Cinema screensaver: instead of a slideshow of posters, the banner experience full screen —
 * muted trailers, the title graphic, the details line and the description — moving to the next title
 * when a trailer ends (or after a few seconds when there is none). Only titles the signed-in person
 * may watch. Returns false (through [onEmpty]) when there is nothing to show, so the caller can fall
 * back to the regular screensaver.
 */
@Composable
fun JellyCinemaScreensaver(onEmpty: @Composable () -> Unit) {
	val repo = koinInject<JellyCinemaRepository>()
	val home = koinInject<JellyCinemaHeroState>()
	// its own state, so the screensaver never changes what the Home banner is showing
	val state = remember { JellyCinemaHeroState() }
	var loaded by remember { mutableStateOf(false) }
	val slides by state.slides.collectAsState()
	val index by state.index.collectAsState()
	val trailerPlaying by state.trailerPlaying.collectAsState()

	LaunchedEffect(Unit) {
		val items = runCatching {
			repo.ensureConfig()
			// reuse Home's titles when they're loaded, start from a different one
			home.slides.value.ifEmpty { repo.heroItems() }.shuffled()
		}.onFailure { Timber.w(it, "Screensaver titles failed") }.getOrDefault(emptyList())
		state.setSlides(items)
		loaded = true
	}

	// next title after the slide time, or when the trailer finishes (HeroBackdrop calls next())
	LaunchedEffect(index, trailerPlaying, slides.size) {
		if (trailerPlaying || slides.size < 2) return@LaunchedEffect
		delay(repo.config.heroSlideSeconds * 1000L)
		state.next()
	}

	if (loaded && slides.isEmpty()) {
		onEmpty()
		return
	}

	Box(Modifier.fillMaxSize().background(JellyCinemaTheme.background)) {
		if (slides.isNotEmpty()) {
			HeroBackdrop(state)
			HeroInfo(
				state = state,
				playFocusRequester = remember { FocusRequester() },
				showButtons = false,
				modifier = Modifier
					.align(Alignment.BottomStart)
					.fillMaxWidth()
					.height(300.dp)
					.padding(bottom = 48.dp),
			)
		}
		// small brand mark in the corner
		Image(
			painter = painterResource(R.drawable.app_logo),
			contentDescription = null,
			modifier = Modifier.align(Alignment.TopEnd).padding(top = 36.dp, end = 48.dp).height(28.dp).alpha(0.8f),
		)
	}
}
