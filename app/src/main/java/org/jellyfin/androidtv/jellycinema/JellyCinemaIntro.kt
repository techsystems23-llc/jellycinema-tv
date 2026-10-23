package org.jellyfin.androidtv.jellycinema

import android.webkit.WebView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.androidtv.R
import org.koin.compose.koinInject

/** The branded intro is shown once per app start (not when coming back from a movie). */
object JellyCinemaIntroState {
	@Volatile var shown = false
	/** True while the intro covers the screen (remote keys are ignored). */
	@Volatile var showing = false

	/** The animation has played (or there is none). Home builds its rows only after this, so the animation never stutters. */
	val animationDone = kotlinx.coroutines.flow.MutableStateFlow(false)

	/** Home has put its rows on screen. */
	val homeRowsShown = kotlinx.coroutines.flow.MutableStateFlow(false)

	/** Waits (at most a few seconds) until the intro animation has played. */
	suspend fun awaitAnimation() {
		if (shown) return
		withTimeoutOrNull(7_000) { animationDone.first { it } }
	}
}

private const val INTRO_SECONDS = 3.4f   // timeline the steps are written in
private const val INTRO_PLAY_MS = 4_500  // played over 4.5 s (about as long as Netflix's intro)

private fun progress(t: Float, start: Float, end: Float) = ((t - start) / (end - start)).coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f
private fun easeBack(x: Float): Float {
	val c1 = 1.70158f; val c3 = c1 + 1f
	return 1f + c3 * (x - 1f).let { it * it * it } + c1 * (x - 1f).let { it * it }
}

/**
 * Short Jelly Cinema intro (like Netflix's) shown over Home on the first start: the popcorn pops in,
 * slides aside and the name wipes in. Home, the banner and the trailer engine load underneath while
 * it plays, so the first screen the person sees is ready to use instead of frozen for a few seconds.
 * Drawn by the app itself (no video), so it looks the same on every TV.
 */
@Composable
fun JellyCinemaIntro() {
	if (JellyCinemaIntroState.shown) { JellyCinemaIntroState.animationDone.value = true; return }
	val context = LocalContext.current
	val heroState = koinInject<JellyCinemaHeroState>()
	val clock = remember { Animatable(0f) }
	var dismiss by remember { mutableStateOf(false) }
	var gone by remember { mutableStateOf(false) }
	val alpha by animateFloatAsState(if (dismiss) 0f else 1f, tween(700), label = "intro-fade",
		finishedListener = { if (dismiss) { gone = true; JellyCinemaIntroState.showing = false; JellyCinemaIntroState.shown = true } })
	if (gone) return

	DisposableEffect(Unit) {
		JellyCinemaIntroState.showing = true
		onDispose { JellyCinemaIntroState.showing = false; JellyCinemaIntroState.shown = true; JellyCinemaIntroState.animationDone.value = true }
	}

	// Warm up the web engine the trailers use. Its first start took seconds and froze Home: load it on
	// a background thread first, then make the first (now quick) WebView before the animation starts.
	LaunchedEffect(Unit) {
		val t = System.currentTimeMillis()
		withContext(Dispatchers.IO) { runCatching { android.webkit.WebSettings.getDefaultUserAgent(context) } }
		val t2 = System.currentTimeMillis()
		runCatching { WebView(context).destroy() }
		android.util.Log.i("JellyCinemaIntro", "web engine ${t2 - t} ms, first WebView ${System.currentTimeMillis() - t2} ms")
	}

	// Play the animation (nothing heavy runs meanwhile), then Home builds its rows behind the finished,
	// still logo, and the intro leaves once Home has its banner and rows (or after a few seconds at most)
	LaunchedEffect(Unit) {
		clock.animateTo(INTRO_SECONDS, tween(INTRO_PLAY_MS, easing = LinearEasing))
		JellyCinemaIntroState.animationDone.value = true
		withTimeoutOrNull(5_000) {
			heroState.slides.first { it.isNotEmpty() }
			JellyCinemaIntroState.homeRowsShown.first { it }
		}
		delay(400) // let the first cards draw before fading out
		dismiss = true
	}

	BoxWithConstraints(Modifier.fillMaxSize().alpha(alpha).background(Color(0xFF101010))) {
		val t = clock.value
		val u = maxWidth / 1920f // layout is designed on a 1920-wide screen
		val popW = u * 217f; val popH = u * 300f
		val wordW = u * 1074f; val wordH = u * 170f
		val total = 217f + 40f + 1074f
		val popFinalX = (1920f - total) / 2f
		val popCenterX = (1920f - 217f) / 2f

		// red cinema glow: fades in, then settles to the page colour
		val glowIn = easeOut(progress(t, 0f, 0.6f))
		val glowOut = easeInOut(progress(t, 2.6f, 3.4f))
		val glow = glowIn * (1f - 0.55f * glowOut)
		Canvas(Modifier.fillMaxSize()) {
			drawRect(
				Brush.radialGradient(
					0f to Color(0xFF5A1414).copy(alpha = glow), 0.45f to Color(0xFF2A0E0E).copy(alpha = glow * 0.8f), 1f to Color.Transparent,
					center = Offset(size.width / 2f, size.height / 2f), radius = size.width * 0.55f,
				)
			)
		}

		// popcorn: pops in at the centre, then slides left to make room for the name
		val pop = easeBack(progress(t, 0.15f, 0.9f))
		val slide = easeInOut(progress(t, 0.95f, 1.55f))
		val popX = popCenterX + (popFinalX - popCenterX) * slide
		Image(
			painter = painterResource(R.drawable.jc_intro_popcorn),
			contentDescription = null,
			modifier = Modifier
				.size(popW, popH)
				.graphicsLayer {
					translationX = (u * popX).toPx()
					translationY = ((maxHeight - popH) / 2f).toPx()
					scaleX = pop; scaleY = pop
					this.alpha = progress(t, 0.15f, 0.35f)
				},
		)

		// name: wipes in from the left, then a light shimmer passes over it
		val wipe = easeOut(progress(t, 1.15f, 1.85f))
		val shimmer = progress(t, 1.9f, 2.6f)
		Image(
			painter = painterResource(R.drawable.jc_intro_word),
			contentDescription = null,
			modifier = Modifier
				.size(wordW, wordH)
				.graphicsLayer {
					translationX = (u * (popFinalX + 217f + 40f)).toPx()
					translationY = ((maxHeight - wordH) / 2f).toPx() + (u * 10f).toPx()
					compositingStrategy = CompositingStrategy.Offscreen
				}
				.drawWithContent {
					clipRect(right = size.width * wipe) { this@drawWithContent.drawContent() }
					if (shimmer > 0f && shimmer < 1f) {
						val x = -size.width * 0.3f + size.width * 1.6f * shimmer
						drawRect(
							Brush.linearGradient(
								0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.55f), 1f to Color.Transparent,
								start = Offset(x - size.width * 0.15f, 0f), end = Offset(x + size.width * 0.15f, size.height),
							),
							blendMode = BlendMode.SrcAtop,
						)
					}
				},
		)
	}
}
