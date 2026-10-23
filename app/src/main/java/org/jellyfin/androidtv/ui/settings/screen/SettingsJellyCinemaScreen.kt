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
import org.jellyfin.androidtv.jellycinema.JellyCinemaSettings
import org.jellyfin.androidtv.jellycinema.SeasonalPreference
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn

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
