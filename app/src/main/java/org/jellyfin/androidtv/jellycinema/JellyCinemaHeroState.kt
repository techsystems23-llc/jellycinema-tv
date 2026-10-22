package org.jellyfin.androidtv.jellycinema

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.sdk.model.api.BaseItemDto

object JellyCinemaTheme {
	val accent = Color(0xFFE0262B)      // popcorn red
	val focus = Color(0xFFFF4D4F)
	val background = Color(0xFF101010)
}

/**
 * Shared state of the Home banner: the slideshow titles, the highlighted card it is following,
 * and Theater Mode. The activity reports every key press here so Theater Mode can wake up
 * (the first key press only wakes it; it never opens or plays anything).
 */
class JellyCinemaHeroState {
	private val _slides = MutableStateFlow<List<BaseItemDto>>(emptyList())
	val slides = _slides.asStateFlow()

	val index = MutableStateFlow(0)

	/** A card highlighted in the rows (banner follows selection), or null for the slideshow. */
	val preview = MutableStateFlow<BaseItemDto?>(null)

	/** The card the person is on right now (preview switches to it after a short pause). */
	val selected = MutableStateFlow<BaseItemDto?>(null)

	private val _theater = MutableStateFlow(false)
	val theater = _theater.asStateFlow()

	/** A trailer is playing on the current slide (slide waits for it). */
	val trailerPlaying = MutableStateFlow(false)

	/** A trailer is being looked up / loading for the current slide (slide waits a while for it). */
	val trailerPending = MutableStateFlow(false)

	/** The trailer's picture is on screen (the backdrop picture fades out). */
	val trailerVisible = MutableStateFlow(false)

	@Volatile
	var lastInput: Long = System.currentTimeMillis()
		private set

	@Volatile
	var homeVisible: Boolean = false

	fun setSlides(items: List<BaseItemDto>) {
		_slides.value = items
		if (index.value >= items.size) index.value = 0
	}

	fun next() {
		val n = _slides.value.size
		if (n > 0) index.value = (index.value + 1) % n
	}

	fun previous() {
		val n = _slides.value.size
		if (n > 0) index.value = (index.value - 1 + n) % n
	}

	fun enterTheater() {
		if (homeVisible) {
			preview.value = null
			_theater.value = true
		}
	}

	/** Every key press. Returns true when it only woke Theater Mode (the key must then be ignored). */
	/** Number of remote key presses (the banner stays full size until the first one). */
	val keys = MutableStateFlow(0)

	/** Last real remote key press (not just Home opening). */
	@Volatile
	var lastKey: Long = 0L
		private set

	fun onInput(fromKey: Boolean = true): Boolean {
		lastInput = System.currentTimeMillis()
		if (fromKey) { lastKey = lastInput; keys.value = keys.value + 1 }
		if (_theater.value) {
			_theater.value = false
			return true
		}
		return false
	}

	fun reset() {
		_slides.value = emptyList()
		index.value = 0
		preview.value = null
		selected.value = null
		_theater.value = false
		trailerPlaying.value = false
		trailerPending.value = false
	}
}
