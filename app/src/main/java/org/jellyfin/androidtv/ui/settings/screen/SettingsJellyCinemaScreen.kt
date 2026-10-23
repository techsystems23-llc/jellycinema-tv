package org.jellyfin.androidtv.ui.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.jellycinema.JellyCinemaDiagnostics
import org.jellyfin.androidtv.jellycinema.PinPad
import org.jellyfin.androidtv.jellycinema.PinTextButton
import org.jellyfin.androidtv.jellycinema.ProfileLock
import org.jellyfin.androidtv.auth.repository.UserRepository
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.koin.compose.koinInject
import org.jellyfin.androidtv.jellycinema.JellyCinemaSettings
import org.jellyfin.androidtv.jellycinema.SeasonalPreference
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn

private enum class PinStep { None, Choose, Confirm, Remove }

/** Settings → Jelly Cinema: the home screen options, changed with the remote (press to step through the choices). */
@Composable
fun SettingsJellyCinemaScreen() {
	val context = LocalContext.current
	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.app_name).uppercase()) },
				headingContent = { Text(stringResource(R.string.jc_settings_title)) },
				captionContent = { Text(stringResource(R.string.jc_settings_description)) },
			)
		}

		item {
			// seasonal effects (automatic by date, off, or preview any season)
			var choice by remember { mutableStateOf(SeasonalPreference.get(context)) }
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_photos), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.jc_setting_seasonal_effects)) },
				captionContent = { Text(SeasonalPreference.label(choice)) },
				onClick = {
					val list = SeasonalPreference.choices
					choice = list[(list.indexOf(choice) + 1) % list.size]
					SeasonalPreference.set(context, choice)
				},
			)
		}

		item {
			// profile PIN for the signed-in profile (asked at start-up and when switching to it)
			val user by koinInject<UserRepository>().currentUser.collectAsState()
			val me = user
			if (me != null) {
				var hasPin by remember(me.id) { mutableStateOf(ProfileLock.hasPin(context, me.id)) }
				var step by remember { mutableStateOf(PinStep.None) }
				var first by remember { mutableStateOf("") }
				var error by remember { mutableStateOf<String?>(null) }
				val noMatch = stringResource(R.string.jc_pin_no_match)
				val wrong = stringResource(R.string.jc_pin_wrong)
				ListButton(
					leadingContent = { Icon(painterResource(R.drawable.ic_users), contentDescription = null) },
					headingContent = { Text(stringResource(R.string.jc_setting_pin)) },
					captionContent = {
						Text(stringResource(if (hasPin) R.string.jc_setting_pin_on else R.string.jc_setting_pin_off, me.name.orEmpty()))
					},
					onClick = { error = null; step = if (hasPin) PinStep.Remove else PinStep.Choose },
				)
				if (step != PinStep.None) Dialog(
					onDismissRequest = { step = PinStep.None },
					properties = DialogProperties(usePlatformDefaultWidth = false),
				) {
					PinPad(
						title = stringResource(when (step) {
							PinStep.Choose -> R.string.jc_pin_choose
							PinStep.Confirm -> R.string.jc_pin_confirm
							else -> R.string.jc_pin_enter_to_remove
						}),
						message = if (step == PinStep.Choose) stringResource(R.string.jc_pin_choose_text, me.name.orEmpty()) else null,
						error = error,
						onDone = { pin ->
							when (step) {
								PinStep.Choose -> { first = pin; error = null; step = PinStep.Confirm }
								PinStep.Confirm -> if (pin == first) {
									ProfileLock.setPin(context, me.id, pin); hasPin = true; step = PinStep.None
								} else { error = noMatch; step = PinStep.Choose }
								PinStep.Remove -> if (ProfileLock.check(context, me.id, pin)) {
									ProfileLock.removePin(context, me.id); hasPin = false; step = PinStep.None
								} else error = wrong
								PinStep.None -> Unit
							}
						},
					) {
						PinTextButton(stringResource(R.string.jc_pin_back)) { step = PinStep.None }
					}
				}
			}
		}

		for (option in JellyCinemaSettings.Option.entries) item {
			var value by remember { mutableStateOf(JellyCinemaSettings.get(option, context)) }
			ListButton(
				headingContent = { Text(stringResource(option.title)) },
				captionContent = { Text(JellyCinemaSettings.label(context, option, value) + "  •  " + stringResource(option.description)) },
				onClick = {
					value = JellyCinemaSettings.next(option, value)
					JellyCinemaSettings.set(context, option, value)
				},
			)
		}

		item {
			// what the banner trailer did last (helps when trailers don't show)
			val status by JellyCinemaDiagnostics.trailer.collectAsState()
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.jc_setting_trailer_status)) },
				captionContent = { Text(status) },
				onClick = {},
			)
		}
	}
}
