package org.jellyfin.androidtv.jellycinema

import android.net.Uri
import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import org.jellyfin.androidtv.R
import org.koin.compose.koinInject

/** The branded intro is shown once per app start (not when coming back from a movie). */
object JellyCinemaIntroState {
	@Volatile var shown = false
	/** True while the intro covers the screen (remote keys are ignored). */
	@Volatile var showing = false
}

/**
 * Short Jelly Cinema intro (like Netflix's) shown over Home on the first start. Home, the banner
 * and the trailer player (WebView) load underneath while it plays, so the first screen the person
 * sees is ready to use instead of frozen for a few seconds.
 *
 * The video plays on a SurfaceView, which keeps animating even while the app is busy setting up.
 */
@Composable
fun JellyCinemaIntro() {
	if (JellyCinemaIntroState.shown) return
	val context = LocalContext.current
	val heroState = koinInject<JellyCinemaHeroState>()
	var videoDone by remember { mutableStateOf(false) }
	var dismiss by remember { mutableStateOf(false) }
	var gone by remember { mutableStateOf(false) }
	val alpha by animateFloatAsState(if (dismiss) 0f else 1f, tween(700), label = "intro-fade",
		finishedListener = { if (dismiss) { gone = true; JellyCinemaIntroState.showing = false; JellyCinemaIntroState.shown = true } })
	if (gone) return

	DisposableEffect(Unit) {
		JellyCinemaIntroState.showing = true
		onDispose { JellyCinemaIntroState.showing = false; JellyCinemaIntroState.shown = true }
	}

	val player = remember {
		ExoPlayer.Builder(context).build().apply {
			setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/${R.raw.jc_intro}")))
			volume = 0f
			playWhenReady = true
			addListener(object : Player.Listener {
				override fun onPlaybackStateChanged(state: Int) {
					if (state == Player.STATE_ENDED) videoDone = true
				}
				override fun onPlayerError(error: androidx.media3.common.PlaybackException) { videoDone = true }
			})
			prepare()
		}
	}
	DisposableEffect(player) { onDispose { player.release() } }

	// Warm up the web engine the trailers use (the first WebView takes a couple of seconds to load)
	LaunchedEffect(Unit) {
		delay(600)
		runCatching { WebView(context).destroy() }
	}

	// Leave when the video has played and Home has its banner (or after a few seconds at most)
	LaunchedEffect(Unit) {
		withTimeoutOrNull(9_000) {
			while (!videoDone) delay(50)
			withTimeoutOrNull(4_500) { heroState.slides.first { it.isNotEmpty() } }
			delay(250)
		}
		videoDone = true
		dismiss = true
	}

	Box(Modifier.fillMaxSize().alpha(alpha).background(Color(0xFF101010))) {
		// last frame stays up (and fades) after the video ends
		Image(
			painter = painterResource(R.drawable.jc_intro_last),
			contentDescription = null,
			contentScale = ContentScale.Crop,
			modifier = Modifier.fillMaxSize(),
		)
		if (!videoDone) {
			AndroidView(
				modifier = Modifier.fillMaxSize(),
				factory = { ctx ->
					PlayerView(ctx).apply {
						useController = false
						isFocusable = false
						resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
						setShutterBackgroundColor(android.graphics.Color.BLACK)
						this.player = player
					}
				},
				onRelease = { it.player = null },
			)
		}
	}
}
