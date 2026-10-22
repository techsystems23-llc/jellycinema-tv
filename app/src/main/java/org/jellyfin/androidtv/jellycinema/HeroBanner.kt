package org.jellyfin.androidtv.jellycinema

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import org.jellyfin.sdk.api.client.extensions.videosApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject
import timber.log.Timber
import java.util.UUID

// ------------------------------------------------------------------------------------------------
// Images

private fun ApiClient.backdropUrl(item: BaseItemDto): String? {
	val tag = item.backdropImageTags?.firstOrNull()
	if (tag != null) return imageApi.getItemImageUrl(item.id, ImageType.BACKDROP, tag = tag, imageIndex = 0, maxWidth = 1920, quality = 90)
	val pTag = item.parentBackdropImageTags?.firstOrNull(); val pId = item.parentBackdropItemId
	if (pTag != null && pId != null) return imageApi.getItemImageUrl(pId, ImageType.BACKDROP, tag = pTag, imageIndex = 0, maxWidth = 1920, quality = 90)
	item.imageTags?.get(ImageType.THUMB)?.let { return imageApi.getItemImageUrl(item.id, ImageType.THUMB, tag = it, maxWidth = 1920) }
	item.imageTags?.get(ImageType.PRIMARY)?.let { return imageApi.getItemImageUrl(item.id, ImageType.PRIMARY, tag = it, maxWidth = 900) }
	return null
}

private fun ApiClient.logoUrl(item: BaseItemDto): String? {
	item.imageTags?.get(ImageType.LOGO)?.let { return imageApi.getItemImageUrl(item.id, ImageType.LOGO, tag = it, maxWidth = 900) }
	val pTag = item.parentLogoImageTag; val pId = item.parentLogoItemId
	if (pTag != null && pId != null) return imageApi.getItemImageUrl(pId, ImageType.LOGO, tag = pTag, maxWidth = 900)
	return null
}

private val BaseItemDto.displayName: String
	get() = if (type == BaseItemKind.EPISODE) (seriesName ?: name ?: "") else (name ?: "")

private fun BaseItemDto.infoBits(): List<String> = buildList {
	productionYear?.let { add(it.toString()) }
	communityRating?.let { add("★ " + String.format("%.1f", it)) }
	criticRating?.let { add("🍅 ${it.toInt()}%") }
	if (type == BaseItemKind.SERIES) childCount?.takeIf { it > 0 }?.let { add(if (it == 1) "1 Season" else "$it Seasons") }
	else runTimeTicks?.let { t ->
		val m = (t / 600_000_000L).toInt()
		if (m > 0) add((if (m >= 60) "${m / 60}h " else "") + (if (m % 60 > 0) "${m % 60}m" else "").trim())
	}
	genres?.take(2)?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" · ").uppercase()) }
}

private fun BaseItemDto.taglineText(): String? = when (type) {
	BaseItemKind.EPISODE -> {
		val se = if (parentIndexNumber != null && parentIndexNumber!! < 1900) "S$parentIndexNumber" + (indexNumber?.let { ":E$it" } ?: "") else ""
		(if (se.isNotEmpty()) "$se · " else "") + (name ?: "")
	}
	else -> taglines?.firstOrNull()
}

private fun youTubeIds(item: BaseItemDto): List<String> = item.remoteTrailers.orEmpty().mapNotNull { t ->
	val u = t.url ?: return@mapNotNull null
	Regex("""(?:youtube(?:-nocookie)?\.com/(?:watch\?(?:.*&)?v=|embed/)|youtu\.be/)([A-Za-z0-9_-]{6,})""").find(u)?.groupValues?.get(1)
}.distinct().filterNot { TrailerMemory.isBad(it) }

private fun findWebView(v: android.view.View): android.webkit.WebView? {
	if (v is android.webkit.WebView) return v
	if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findWebView(v.getChildAt(i))?.let { return it }
	return null
}

/** YouTube trailers that refused to play in an embedded player (kept on the TV, so they're skipped next time). */
object TrailerMemory {
	private const val PREFS = "jellycinema_trailers2"
	@Volatile private var bad: MutableSet<String>? = null
	fun load(context: android.content.Context) {
		if (bad == null) bad = context.getSharedPreferences(PREFS, 0).getStringSet("bad", emptySet()).orEmpty().toMutableSet()
	}
	fun isBad(id: String) = bad?.contains(id) == true

	/** Titles whose trailer can play go first; titles whose every trailer was refused go last. */
	fun playableFirst(items: List<BaseItemDto>): List<BaseItemDto> {
		if (bad.isNullOrEmpty()) return items
		val (ok, refused) = items.partition { (it.localTrailerCount ?: 0) > 0 || youTubeIds(it).isNotEmpty() || it.remoteTrailers.isNullOrEmpty() }
		return ok + refused
	}
	fun markBad(context: android.content.Context, id: String) {
		load(context)
		bad?.add(id)
		context.getSharedPreferences(PREFS, 0).edit().putStringSet("bad", bad?.toSet()).apply()
	}
}

// ------------------------------------------------------------------------------------------------
// Backdrop: the full-screen picture (and trailer) behind everything on Home

@Composable
fun HeroBackdrop(state: JellyCinemaHeroState, modifier: Modifier = Modifier) {
	TrailerMemory.load(LocalContext.current)
	val api = koinInject<ApiClient>()
	val repo = koinInject<JellyCinemaRepository>()
	val slides by state.slides.collectAsState()
	val index by state.index.collectAsState()
	val preview by state.preview.collectAsState()
	val theater by state.theater.collectAsState()
	val current = preview ?: slides.getOrNull(index)
	val trailerOn by state.trailerVisible.collectAsState()
	val pictureAlpha by animateFloatAsState(if (trailerOn) 0f else 1f, tween(1200), label = "picture-alpha")

	// The trailer sits underneath the picture; the picture fades away once the trailer is playing.
	Box(modifier.fillMaxSize().background(JellyCinemaTheme.background)) {
		// Trailer (muted): the title's own trailer file if the library has one, else its YouTube trailer
		val cfg = repo.config
		val trailerItem = current
		if (cfg.heroTrailers && trailerItem != null) {
			var source by remember(trailerItem.id) { mutableStateOf<TrailerSource?>(null) }
			LaunchedEffect(trailerItem.id) {
				state.trailerPlaying.value = false
				state.trailerVisible.value = false
				state.trailerPending.value = true
				delay(cfg.heroTrailerDelaySeconds * 1000L)
				source = resolveTrailer(api, trailerItem)
				if (source == null || source == TrailerSource.None) state.trailerPending.value = false
			}
			val onPlaying: (Boolean) -> Unit = { state.trailerPending.value = false; state.trailerPlaying.value = it }
			val onFinished: () -> Unit = {
				state.trailerPlaying.value = false
				if (state.preview.value == null) state.next()
			}
			when (val src = source) {
				is TrailerSource.Local -> LocalTrailerView(src.url, cfg.trailerMaxSeconds, onPlaying, onFinished,
					onVisible = { state.trailerVisible.value = it },
					onFailed = { source = src.fallback.takeIf { it.isNotEmpty() }?.let { TrailerSource.YouTube(it) } ?: TrailerSource.None })
				is TrailerSource.YouTube -> TrailerView(src.ids, api.baseUrl, onPlaying, { state.trailerVisible.value = it }, onFinished)
				TrailerSource.None -> LaunchedEffect(Unit) { state.trailerPending.value = false }
				else -> Unit
			}
		} else {
			LaunchedEffect(Unit) { state.trailerPlaying.value = false; state.trailerPending.value = false }
		}


		AnimatedContent(
			targetState = current,
			transitionSpec = { fadeIn(tween(900)) togetherWith fadeOut(tween(900)) },
			contentKey = { it?.id },
			label = "hero-backdrop",
		) { item ->
			if (item != null) {
				// slow "Ken Burns" zoom while a title is shown
				var zoomed by remember(item.id) { mutableStateOf(false) }
				LaunchedEffect(item.id) { zoomed = true }
				val zoom by animateFloatAsState(if (zoomed) 1.1f else 1.02f, tween(24_000, easing = LinearEasing), label = "ken-burns")
				AsyncImage(
					model = api.backdropUrl(item),
					contentDescription = null,
					contentScale = ContentScale.Crop,
					modifier = Modifier.fillMaxSize().scale(zoom).alpha(pictureAlpha),
				)
			}
		}

		// left shade for the text, bottom fade into the page
		Box(
			Modifier.fillMaxSize().background(
				Brush.horizontalGradient(
					0f to Color(0xEB08090E), 0.28f to Color(0xB808090E), 0.6f to Color(0x2E08090E), 0.78f to Color.Transparent,
				)
			)
		)
		Box(
			Modifier.fillMaxSize().background(
				Brush.verticalGradient(
					0f to Color(0x99000000), 0.12f to Color.Transparent,
					0.45f to Color.Transparent,
					(if (theater) 0.8f else 0.62f) to Color(0xB3101010),
					1f to JellyCinemaTheme.background,
				)
			)
		)
	}
}

/**
 * A YouTube trailer through YouTube's own embedded player (the official IFrame player, via the
 * Apache-2.0 android-youtube-player library), muted, behind the banner picture. The picture fades
 * away once the trailer is really playing.
 */
@Composable
private fun BoxScope.TrailerView(
	videoIds: List<String>,
	baseUrl: String?,
	onPlaying: (Boolean) -> Unit,
	onVisible: (Boolean) -> Unit,
	onFinished: () -> Unit,
) {
	val repo = koinInject<JellyCinemaRepository>()
	val maxMs = repo.config.trailerMaxSeconds * 1000L
	var playing by remember { mutableStateOf(false) }
	LaunchedEffect(playing) {
		if (playing) { delay(maxMs); onFinished() }
	}
	AndroidView(
		modifier = Modifier.fillMaxSize(),
		factory = { context ->
			YouTubePlayerView(context).apply {
				enableAutomaticInitialization = false
				isFocusable = false
				descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
				val options = IFramePlayerOptions.Builder(context)
					.controls(0).rel(0).ivLoadPolicy(3).ccLoadPolicy(0).fullscreen(0).autoplay(1).mute(1)
					.apply { baseUrl?.let { origin(it.trimEnd('/')) } }
					.build()
				var which = 0
				initialize(object : AbstractYouTubePlayerListener() {
					override fun onReady(youTubePlayer: YouTubePlayer) {
						youTubePlayer.mute()
						youTubePlayer.loadVideo(videoIds[which], 0f)
					}
					override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
						when (state) {
							PlayerConstants.PlayerState.PLAYING -> {
								if (!playing) JellyCinemaDiagnostics.trailer("YouTube trailer playing (${videoIds[which]})")
								playing = true; onPlaying(true); onVisible(true)
							}
							PlayerConstants.PlayerState.ENDED -> { playing = false; onVisible(false); onPlaying(false); onFinished() }
							else -> Unit
						}
					}
					var lastCode = ""
					override fun onVideoId(youTubePlayer: YouTubePlayer, videoId: String) {
						// our player page reports YouTube's raw error number this way (see res/raw/ayp_youtube_player.html)
						if (videoId.startsWith("jc-error-")) lastCode = videoId.removePrefix("jc-error-")
					}
					override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
						val code = lastCode.also { lastCode = "" }
						android.util.Log.i("JellyCinemaTrailer", "error ${videoIds.getOrNull(which)} $error code=$code")
						JellyCinemaDiagnostics.trailer("YouTube trailer ${videoIds.getOrNull(which)} refused: $error ($code)")
						// remember trailers YouTube will never play here (removed, embedding off, age-restricted = 151), so they aren't tried again
						if (code in setOf("100", "101", "150", "151")) videoIds.getOrNull(which)?.let { TrailerMemory.markBad(context, it) }
						which++
						if (which < videoIds.size && which < 4) youTubePlayer.loadVideo(videoIds[which], 0f)
						else { playing = false; onVisible(false); onPlaying(false) }
					}
					override fun onPlaybackQualityChange(youTubePlayer: YouTubePlayer, playbackQuality: PlayerConstants.PlaybackQuality) {
						android.util.Log.i("JellyCinemaTrailer", "quality $playbackQuality")
						JellyCinemaDiagnostics.trailer("YouTube trailer playing (${videoIds.getOrNull(which)}, $playbackQuality)")
					}
				}, false, options)
				// YouTube picks the picture quality from the player's size in web pixels. On a TV a full-screen
				// banner is only 960 web pixels wide, so it chose 480p (pixelated). Draw the page at half scale:
				// the player becomes 1920 web pixels wide and YouTube sends 1080p.
				post { findWebView(this)?.let { wv -> wv.setInitialScale(50); wv.settings.useWideViewPort = false } }
			}
		},
		onRelease = { it.release(); onVisible(false); onPlaying(false) },
	)
}

private sealed interface TrailerSource {
	/** A video the app's own player shows; [fallback] = YouTube ids for the web player if it can't. */
	data class Local(val url: String, val fallback: List<String> = emptyList()) : TrailerSource
	data class YouTube(val ids: List<String>) : TrailerSource
	data object None : TrailerSource
}

private suspend fun resolveTrailer(api: ApiClient, item: BaseItemDto): TrailerSource = withContext(Dispatchers.IO) {
	// episodes: use the show's trailer
	val target = if (item.type == BaseItemKind.EPISODE && item.seriesId != null)
		runCatching { api.userLibraryApi.getItem(itemId = item.seriesId!!).content }.getOrNull() ?: item else item
	val local = if ((target.localTrailerCount ?: 0) > 0 || target.localTrailerCount == null)
		runCatching { api.userLibraryApi.getLocalTrailers(itemId = target.id).content.firstOrNull() }.getOrNull() else null
	JellyCinemaDiagnostics.trailer("${target.name}: local=${local?.id} youtube=${youTubeIds(target)} remote=${target.remoteTrailers?.size}")
	if (local != null) return@withContext TrailerSource.Local(api.videosApi.getVideoStreamUrl(itemId = local.id, static = true), youTubeIds(target))
	val ids = youTubeIds(target)
	if (ids.isNotEmpty()) TrailerSource.YouTube(ids) else TrailerSource.None
}

/** A trailer file from the library, muted, filling the banner. */
@Composable
private fun BoxScope.LocalTrailerView(url: String, maxSeconds: Int, onPlaying: (Boolean) -> Unit, onFinished: () -> Unit, onVisible: (Boolean) -> Unit, onFailed: () -> Unit) {
	val context = LocalContext.current
	var playing by remember { mutableStateOf(false) }
	val alpha by animateFloatAsState(if (playing) 1f else 0f, tween(1200), label = "local-trailer-alpha")
	val player = remember(url) {
		ExoPlayer.Builder(context).build().apply {
			volume = 0f
			repeatMode = Player.REPEAT_MODE_OFF
			setMediaItem(MediaItem.fromUri(url))
			prepare()
			playWhenReady = true
		}
	}
	DisposableEffect(player) {
		val listener = object : Player.Listener {
			override fun onIsPlayingChanged(isPlaying: Boolean) {
				if (isPlaying) onPlaying(true)
			}
			override fun onRenderedFirstFrame() {
				// only now hide the picture: the trailer is really on screen
				playing = true; onVisible(true)
				JellyCinemaDiagnostics.trailer("Trailer showing in the banner")
			}
			override fun onPlaybackStateChanged(state: Int) {
				if (state == Player.STATE_ENDED) { playing = false; onVisible(false); onPlaying(false); onFinished() }
			}
			override fun onPlayerError(error: PlaybackException) {
				JellyCinemaDiagnostics.trailer("Trailer couldn't play: ${error.errorCodeName}")
				playing = false; onVisible(false); onPlaying(false); onFailed()
			}
		}
		player.addListener(listener)
		onDispose { player.removeListener(listener); player.release(); onVisible(false); onPlaying(false) }
	}
	LaunchedEffect(playing) {
		if (playing) { delay(maxSeconds * 1000L); onFinished() }
	}
	AndroidView(
		modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
		factory = { ctx ->
			(android.view.LayoutInflater.from(ctx).inflate(org.jellyfin.androidtv.R.layout.jc_trailer_player, null) as PlayerView).apply {
				useController = false
				isFocusable = false
				resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
				setShutterBackgroundColor(AColor.TRANSPARENT)
				this.player = player
			}
		},
	)
}

// ------------------------------------------------------------------------------------------------
// Info block: logo / title, details line, description and buttons

@Composable
fun HeroInfo(
	state: JellyCinemaHeroState,
	playFocusRequester: FocusRequester,
	modifier: Modifier = Modifier,
	compact: Boolean = false,
	showButtons: Boolean = true,
	onBannerFocused: () -> Unit = {},
) {
	val api = koinInject<ApiClient>()
	val navigation = koinInject<NavigationRepository>()
	val playback = koinInject<PlaybackHelper>()
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val slides by state.slides.collectAsState()
	val index by state.index.collectAsState()
	val preview by state.preview.collectAsState()
	val theater by state.theater.collectAsState()
	val item = preview ?: slides.getOrNull(index) ?: return
	val buttonsAlpha by animateFloatAsState(if (theater || compact) 0f else 1f, tween(400), label = "hero-buttons")

	var favorites by remember { mutableStateOf<Map<UUID, Boolean>>(emptyMap()) }
	val isFavorite = favorites[item.id] ?: (item.userData?.isFavorite == true)

	Column(modifier.padding(start = 48.dp, end = 48.dp), verticalArrangement = Arrangement.Bottom) {
		AnimatedContent(
			targetState = item,
			transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(400)) },
			contentKey = { it.id },
			label = "hero-info",
		) { shown ->
			Column(Modifier.widthIn(max = 470.dp)) {
				val logo = api.logoUrl(shown)
				var logoFailed by remember(shown.id) { mutableStateOf(false) }
				if (logo != null && !logoFailed) {
					AsyncImage(
						model = logo,
						contentDescription = shown.displayName,
						contentScale = ContentScale.Fit,
						alignment = Alignment.BottomStart,
						onError = { logoFailed = true },
						modifier = Modifier.heightIn(max = if (compact) 60.dp else 80.dp).widthIn(max = if (compact) 260.dp else 330.dp),
					)
				} else {
					Text(
						shown.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis,
						style = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, lineHeight = 40.sp),
					)
				}
				Spacer(Modifier.height(10.dp))
				Box(Modifier.width(40.dp).height(2.dp).background(Color.White.copy(alpha = 0.55f)))
				Spacer(Modifier.height(9.dp))
				val bits = shown.infoBits()
				Row(verticalAlignment = Alignment.CenterVertically) {
					bits.forEachIndexed { i, b ->
						if (i > 0) Text("  •  ", style = TextStyle(fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f)))
						Text(b, style = TextStyle(fontSize = 12.sp, letterSpacing = 1.2.sp, color = Color.White.copy(alpha = 0.9f)))
					}
					shown.officialRating?.let {
						if (bits.isNotEmpty()) Text("  •  ", style = TextStyle(fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f)))
						Text(it, modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
							style = TextStyle(fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f)))
					}
				}
				shown.taglineText()?.takeIf { it.isNotBlank() }?.let {
					Spacer(Modifier.height(8.dp))
					Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
				}
				Spacer(Modifier.height(6.dp))
				if (!compact) Text(
					shown.overview ?: "", maxLines = 2, overflow = TextOverflow.Ellipsis,
					style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = Color.White.copy(alpha = 0.82f)),
				)
			}
		}

		if (showButtons) {
		Spacer(Modifier.height(if (compact) 0.dp else 16.dp))
		val resume = (item.userData?.playbackPositionTicks ?: 0L) > 0L
		Row(
			modifier = Modifier
				.then(if (compact) Modifier.height(0.dp) else Modifier)
				.alpha(buttonsAlpha)
				.onFocusChanged { if (it.hasFocus) onBannerFocused() }
				.onPreviewKeyEvent { e ->
					if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
					false
				},
			horizontalArrangement = Arrangement.spacedBy(14.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			HeroButton(
				label = if (resume) "Resume" else "Play", primary = true, icon = R.drawable.ic_play,
				modifier = Modifier.focusRequester(playFocusRequester)
					.onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { state.previous(); true } else false },
			) { playback.retrieveAndPlay(item.id, false, context) }
			HeroButton(label = "More info", icon = R.drawable.ic_info) {
				navigation.navigate(Destinations.itemDetails(if (item.type == BaseItemKind.EPISODE) item.id else item.id))
			}
			HeroButton(
				label = if (isFavorite) "Favorite" else "Add to favorites", icon = R.drawable.ic_heart, active = isFavorite,
				modifier = Modifier.onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { state.next(); true } else false },
			) {
				val want = !isFavorite
				favorites = favorites + (item.id to want)
				scope.launch(Dispatchers.IO) {
					runCatching {
						if (want) api.userLibraryApi.markFavoriteItem(itemId = item.id) else api.userLibraryApi.unmarkFavoriteItem(itemId = item.id)
					}.onFailure { withContext(Dispatchers.Main) { favorites = favorites + (item.id to !want) } }
				}
			}
			Spacer(Modifier.width(18.dp))
			// slide dots
			// many titles: a small "12 / 50" instead of a long row of dots
			if (preview == null && slides.size > 15) Text("${index + 1} / ${slides.size}",
				style = TextStyle(fontSize = 13.sp, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.7f)))
			else if (preview == null && slides.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
				slides.indices.forEach { i ->
					Box(Modifier.height(5.dp).width(if (i == index) 20.dp else 5.dp).clip(CircleShape)
						.background(if (i == index) Color.White else Color.White.copy(alpha = 0.35f)))
				}
			}
		}
		}
	}
}

@Composable
private fun HeroButton(
	label: String,
	icon: Int,
	modifier: Modifier = Modifier,
	primary: Boolean = false,
	active: Boolean = false,
	onClick: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "hero-button")
	val shape = if (primary) RoundedCornerShape(50) else CircleShape
	val bg = when {
		primary -> if (focused) Color.White else Color.White.copy(alpha = 0.9f)
		focused -> Color.White.copy(alpha = 0.24f)
		else -> Color.White.copy(alpha = 0.08f)
	}
	val fg = if (primary) Color(0xFF111111) else if (active) JellyCinemaTheme.accent else Color.White
	Box(
		modifier = modifier
			.scale(scale)
			.then(if (focused) Modifier.border(3.dp, JellyCinemaTheme.focus, shape) else if (!primary) Modifier.border(2.dp, Color.White.copy(alpha = 0.45f), shape) else Modifier)
			.clip(shape)
			.background(bg)
			.onFocusChanged { focused = it.isFocused }
			.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
			.then(if (primary) Modifier.height(44.dp).padding(horizontal = 26.dp) else Modifier.size(44.dp)),
		contentAlignment = Alignment.Center,
	) {
		if (primary) Row(verticalAlignment = Alignment.CenterVertically) {
			Icon(painterResource(icon), contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
			Spacer(Modifier.width(8.dp))
			Text(label, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = fg))
		} else Icon(painterResource(icon), contentDescription = label, tint = fg, modifier = Modifier.size(20.dp))
	}
}
