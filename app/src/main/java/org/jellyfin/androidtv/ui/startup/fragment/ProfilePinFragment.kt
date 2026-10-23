package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.jellycinema.JellyCinemaTheme
import org.jellyfin.androidtv.jellycinema.PinPad
import org.jellyfin.androidtv.jellycinema.PinTextButton
import org.jellyfin.androidtv.jellycinema.ProfileLock
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
import org.koin.compose.koinInject

/**
 * Jelly Cinema profile PIN at start-up / when switching to a profile that has one (Settings → Jelly Cinema
 * → Profile PIN). "Other profile" goes back to the profile list; "Forgot PIN" removes the PIN after the
 * profile's Jellyfin password is entered.
 */
@Composable
fun ProfilePinScreen(onUnlocked: () -> Unit, onOtherProfile: () -> Unit) {
	val context = LocalContext.current
	val api = koinInject<ApiClient>()
	val user by koinInject<UserRepository>().currentUser.collectAsState()
	val me = user ?: return
	val scope = rememberCoroutineScope()
	var error by remember { mutableStateOf<String?>(null) }
	var wrong by remember { mutableIntStateOf(0) }
	var waitUntil by remember { mutableStateOf(0L) }
	var forgot by remember { mutableStateOf(false) }
	val wrongText = stringResource(R.string.jc_pin_wrong)
	val waitText = stringResource(R.string.jc_pin_wait)

	if (!forgot) {
		PinPad(
			title = stringResource(R.string.jc_pin_enter_for, me.name.orEmpty()),
			message = null,
			error = error,
			onDone = { pin ->
				when {
					System.currentTimeMillis() < waitUntil -> error = waitText
					ProfileLock.check(context, me.id, pin) -> { ProfileLock.markUnlocked(me.id); onUnlocked() }
					else -> {
						wrong++
						error = wrongText
						// after five wrong tries, a short wait before the next one
						if (wrong % 5 == 0) { waitUntil = System.currentTimeMillis() + 30_000; error = waitText }
					}
				}
			},
		) {
			PinTextButton(stringResource(R.string.jc_pin_other_profile)) { onOtherProfile() }
			PinTextButton(stringResource(R.string.jc_pin_forgot)) { forgot = true }
		}
	} else {
		var password by remember { mutableStateOf("") }
		var busy by remember { mutableStateOf(false) }
		val field = remember { FocusRequester() }
		LaunchedEffect(Unit) { delay(100); runCatching { field.requestFocus() } }
		val badPassword = stringResource(R.string.jc_pin_bad_password)
		fun submit() {
			if (busy || password.isEmpty()) return
			busy = true
			scope.launch {
				val ok = runCatching {
					withContext(Dispatchers.IO) { api.userApi.authenticateUserByName(me.name.orEmpty(), password) }
				}.isSuccess
				busy = false
				if (ok) { ProfileLock.removePin(context, me.id); ProfileLock.markUnlocked(me.id); onUnlocked() }
				else error = badPassword
			}
		}
		Box(Modifier.fillMaxSize().background(JellyCinemaTheme.background), contentAlignment = Alignment.Center) {
			Column(Modifier.widthIn(max = 560.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
				Text(stringResource(R.string.jc_pin_forgot_title), style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center))
				Spacer(Modifier.height(8.dp))
				Text(stringResource(R.string.jc_pin_forgot_text, me.name.orEmpty()), style = TextStyle(fontSize = 15.sp, color = Color(0xFFBBBBBB), textAlign = TextAlign.Center))
				Spacer(Modifier.height(20.dp))
				BasicTextField(
					value = password,
					onValueChange = { password = it },
					singleLine = true,
					visualTransformation = PasswordVisualTransformation(),
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
					keyboardActions = KeyboardActions(onDone = { submit() }),
					textStyle = TextStyle(fontSize = 20.sp, color = Color.White),
					cursorBrush = SolidColor(Color.White),
					modifier = Modifier
						.focusRequester(field)
						.width(360.dp)
						.border(2.dp, JellyCinemaTheme.accent, RoundedCornerShape(8.dp))
						.background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
						.padding(horizontal = 14.dp, vertical = 12.dp),
				)
				Spacer(Modifier.height(10.dp))
				Text(error ?: " ", style = TextStyle(fontSize = 15.sp, color = Color(0xFFFF6B6B)))
				Spacer(Modifier.height(10.dp))
				androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
					PinTextButton(stringResource(R.string.jc_pin_remove_with_password)) { submit() }
					PinTextButton(stringResource(R.string.jc_pin_back)) { forgot = false; error = null }
				}
			}
		}
	}
}

class ProfilePinFragment : Fragment() {
	var onUnlocked: () -> Unit = {}
	var onOtherProfile: () -> Unit = {}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = content {
		JellyfinTheme {
			ProfilePinScreen(onUnlocked = { onUnlocked() }, onOtherProfile = { onOtherProfile() })
		}
	}
}
