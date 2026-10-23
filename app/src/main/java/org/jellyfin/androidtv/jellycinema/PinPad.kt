package org.jellyfin.androidtv.jellycinema

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text

/**
 * Number pad for a profile PIN, made for the remote: move with the arrows and press OK, or type the
 * digits on the remote's number keys. Calls [onDone] when all digits are in.
 */
@Composable
fun PinPad(
	title: String,
	message: String?,
	error: String?,
	onDone: (String) -> Unit,
	modifier: Modifier = Modifier,
	buttons: @Composable () -> Unit = {},
) {
	var pin by remember { mutableStateOf("") }
	fun add(d: Char) {
		if (pin.length >= ProfileLock.LENGTH) return
		pin += d
		if (pin.length == ProfileLock.LENGTH) { val p = pin; pin = ""; onDone(p) }
	}
	val first = remember { FocusRequester() }
	LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

	Box(
		modifier
			.fillMaxSize()
			.background(JellyCinemaTheme.background)
			.onPreviewKeyEvent { e ->
				val code = e.nativeKeyEvent.keyCode
				if (e.type == KeyEventType.KeyDown && code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) { add('0' + (code - KeyEvent.KEYCODE_0)); true }
				else if (e.type == KeyEventType.KeyDown && code == KeyEvent.KEYCODE_DEL && pin.isNotEmpty()) { pin = pin.dropLast(1); true }
				else false
			},
		contentAlignment = Alignment.Center,
	) {
		Column(Modifier.widthIn(max = 560.dp).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
			Text(title, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center))
			if (message != null) {
				Spacer(Modifier.height(8.dp))
				Text(message, style = TextStyle(fontSize = 15.sp, color = Color(0xFFBBBBBB), textAlign = TextAlign.Center))
			}
			Spacer(Modifier.height(14.dp))
			Row(
				horizontalArrangement = Arrangement.spacedBy(16.dp),
				modifier = Modifier.semantics { contentDescription = "${pin.length} / ${ProfileLock.LENGTH}" },
			) {
				repeat(ProfileLock.LENGTH) { i ->
					Box(Modifier.size(14.dp).background(if (i < pin.length) Color.White else Color.White.copy(alpha = 0.18f), CircleShape))
				}
			}
			Spacer(Modifier.height(10.dp))
			Text(error ?: " ", style = TextStyle(fontSize = 15.sp, color = Color(0xFFFF6B6B), textAlign = TextAlign.Center))
			Spacer(Modifier.height(10.dp))
			val rows = listOf("123", "456", "789")
			for (r in rows) {
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					for (c in r) PadKey(c.toString(), if (c == '1') Modifier.focusRequester(first) else Modifier) { add(c) }
				}
				Spacer(Modifier.height(10.dp))
			}
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Spacer(Modifier.size(54.dp))
				PadKey("0") { add('0') }
				PadKey("⌫", label = stringResource(R.string.jc_pin_delete)) { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
			}
			Spacer(Modifier.height(14.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { buttons() }
		}
	}
}

@Composable
private fun PadKey(text: String, modifier: Modifier = Modifier, label: String = text, onClick: () -> Unit) {
	var focused by remember { mutableStateOf(false) }
	Box(
		modifier
			.size(54.dp)
			.onFocusChanged { focused = it.isFocused }
			.then(if (focused) Modifier.border(3.dp, JellyCinemaTheme.accent, CircleShape) else Modifier)
			.background(if (focused) Color.White else Color.White.copy(alpha = 0.1f), CircleShape)
			.semantics { contentDescription = label }
			.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Text(text, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (focused) Color(0xFF111111) else Color.White))
	}
}

/** A wide button under the number pad ("Other profile", "Forgot PIN"...). */
@Composable
fun PinTextButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
	var focused by remember { mutableStateOf(false) }
	val shape = RoundedCornerShape(50)
	Box(
		modifier
			.onFocusChanged { focused = it.isFocused }
			.then(if (focused) Modifier.border(3.dp, JellyCinemaTheme.accent, shape) else Modifier)
			.background(if (focused) Color.White else Color.White.copy(alpha = 0.1f), shape)
			.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
			.padding(horizontal = 22.dp, vertical = 11.dp),
	) {
		Text(text, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (focused) Color(0xFF111111) else Color.White))
	}
}
