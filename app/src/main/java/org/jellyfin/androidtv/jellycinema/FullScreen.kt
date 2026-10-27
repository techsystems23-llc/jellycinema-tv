package org.jellyfin.androidtv.jellycinema

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Real TVs have no status or navigation bar, but many Android TV boxes (and Android-x86 builds) show them.
 * Hide them so the app always fills the screen; they come back briefly with a swipe or the box's own keys.
 */
fun Activity.hideSystemBars() {
	runCatching {
		WindowCompat.getInsetsController(window, window.decorView).apply {
			systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
			hide(WindowInsetsCompat.Type.systemBars())
		}
	}
}
