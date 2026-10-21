package org.jellyfin.androidtv.integration.dream.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jellyfin.androidtv.integration.dream.DreamViewModel
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.ClockBehavior
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun DreamHost() {
	val viewModel = koinViewModel<DreamViewModel>()
	val userPreferences = koinInject<UserPreferences>()
	val content by viewModel.content.collectAsState()

	val regular = @Composable {
		DreamView(
			content = content,
			showClock = when (userPreferences[UserPreferences.clockBehavior]) {
				ClockBehavior.ALWAYS, ClockBehavior.IN_MENUS -> true
				else -> false
			}
		)
	}

	// Jelly Cinema: trailers, title graphics and descriptions instead of a poster slideshow
	// (music playing keeps the regular "now playing" screen)
	if (content is org.jellyfin.androidtv.integration.dream.model.DreamContent.NowPlaying) regular()
	else org.jellyfin.androidtv.jellycinema.JellyCinemaScreensaver(onEmpty = regular)
}
