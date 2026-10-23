package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.jellycinema.JellyCinemaTheme
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.koin.compose.koinInject

private const val RETRY_SECONDS = 10

/**
 * Shown at start-up when the saved sign-in is fine but the server isn't answering (down, restarting,
 * network). It keeps trying by itself, so nobody is sent to the sign-in screen thinking their password is wrong.
 */
@Composable
fun ServerUnreachableScreen(onChooseServer: () -> Unit) {
	val sessionRepository = koinInject<SessionRepository>()
	val name by sessionRepository.unreachableServer.collectAsState()
	val scope = rememberCoroutineScope()
	var seconds by remember { mutableIntStateOf(RETRY_SECONDS) }
	var attempt by remember { mutableIntStateOf(0) }
	var trying by remember { mutableStateOf(false) }

	suspend fun retry() {
		trying = true
		sessionRepository.restoreSession(destroyOnly = false)
		trying = false
		attempt++
	}

	// try again every few seconds until the server answers (the start screen then moves on by itself)
	LaunchedEffect(attempt) {
		seconds = RETRY_SECONDS
		while (seconds > 0) { delay(1_000); seconds-- }
		retry()
	}

	val tryFocus = remember { FocusRequester() }
	LaunchedEffect(Unit) { runCatching { tryFocus.requestFocus() } }

	Box(Modifier.fillMaxSize().background(JellyCinemaTheme.background), contentAlignment = Alignment.Center) {
		Column(Modifier.widthIn(max = 640.dp).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
			Image(painterResource(R.drawable.app_logo), contentDescription = stringResource(R.string.app_name), modifier = Modifier.height(48.dp))
			Spacer(Modifier.height(36.dp))
			Text(
				stringResource(R.string.jc_unreachable_title, name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.jc_unreachable_your_server)),
				style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center),
			)
			Spacer(Modifier.height(14.dp))
			Text(
				stringResource(R.string.jc_unreachable_text),
				style = TextStyle(fontSize = 17.sp, color = Color(0xFFCCCCCC), textAlign = TextAlign.Center),
			)
			Spacer(Modifier.height(10.dp))
			Text(
				if (trying) stringResource(R.string.jc_unreachable_trying) else stringResource(R.string.jc_unreachable_countdown, seconds),
				style = TextStyle(fontSize = 15.sp, color = Color(0xFF999999), textAlign = TextAlign.Center),
			)
			Spacer(Modifier.height(30.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				UnreachableButton(stringResource(R.string.jc_unreachable_try_now), Modifier.focusRequester(tryFocus)) {
					if (!trying) scope.launch { retry() }
				}
				UnreachableButton(stringResource(R.string.jc_unreachable_choose_server)) { onChooseServer() }
			}
		}
	}
}

@Composable
private fun UnreachableButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
	var focused by remember { mutableStateOf(false) }
	val shape = RoundedCornerShape(50)
	Box(
		modifier
			.onFocusChanged { focused = it.isFocused }
			.then(if (focused) Modifier.border(3.dp, JellyCinemaTheme.accent, shape) else Modifier)
			.background(if (focused) Color.White else Color.White.copy(alpha = 0.12f), shape)
			.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
			.padding(horizontal = 26.dp, vertical = 12.dp),
	) {
		Text(label, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (focused) Color(0xFF111111) else Color.White))
	}
}

class ServerUnreachableFragment : Fragment() {
	var onChooseServer: () -> Unit = {}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = content {
		JellyfinTheme {
			ServerUnreachableScreen(onChooseServer = { onChooseServer() })
		}
	}
}
