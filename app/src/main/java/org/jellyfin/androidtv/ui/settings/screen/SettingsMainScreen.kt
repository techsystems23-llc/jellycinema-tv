package org.jellyfin.androidtv.ui.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.jellycinema.UpdateChecker
import org.jellyfin.androidtv.jellycinema.SeasonalPreference
import org.jellyfin.androidtv.jellycinema.JellyCinemaDiagnostics
import androidx.compose.runtime.collectAsState
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.compose.koinInject
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn

@Composable
fun SettingsMainScreen() {
	val router = LocalRouter.current

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.app_name).uppercase()) },
				headingContent = { Text(stringResource(R.string.settings)) },
				captionContent = { Text(stringResource(R.string.settings_description)) },
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_users), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_login)) },
				onClick = { router.push(Routes.AUTHENTICATION) },
				modifier = Modifier.focusKey(Routes.AUTHENTICATION),
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_adjust), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_customization)) },
				onClick = { router.push(Routes.CUSTOMIZATION) },
				modifier = Modifier.focusKey(Routes.CUSTOMIZATION),
			)
		}

		// TODO: Temporarily added to root - should be accessed via customization screen instead
		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_photos), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_screensaver)) },
				onClick = { router.push(Routes.CUSTOMIZATION_SCREENSAVER) },
				modifier = Modifier.focusKey(Routes.CUSTOMIZATION_SCREENSAVER),
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_next), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_playback)) },
				onClick = { router.push(Routes.PLAYBACK) },
				modifier = Modifier.focusKey(Routes.PLAYBACK),
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_error), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_telemetry_category)) },
				onClick = { router.push(Routes.TELEMETRY) },
				modifier = Modifier.focusKey(Routes.TELEMETRY),
			)

		}

		item {
			// Jelly Cinema: seasonal effects (automatic by date, off, or preview any season)
			val context = LocalContext.current
			var choice by remember { mutableStateOf(SeasonalPreference.get(context)) }
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_photos), contentDescription = null) },
				headingContent = { Text("Seasonal effects") },
				captionContent = { Text(SeasonalPreference.label(choice) + "  \u2022  press to change") },
				onClick = {
					val list = SeasonalPreference.choices
					choice = list[(list.indexOf(choice) + 1) % list.size]
					SeasonalPreference.set(context, choice)
				},
			)
		}

		item {
			// Jelly Cinema: what the banner trailer did last (helps when trailers don't show)
			val status by JellyCinemaDiagnostics.trailer.collectAsState()
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
				headingContent = { Text("Banner trailers") },
				captionContent = { Text(status) },
				onClick = {},
			)
		}

		item {
			// Jelly Cinema: update the app from the Jellyfin server it is connected to
			val context = LocalContext.current
			val api = koinInject<ApiClient>()
			val scope = rememberCoroutineScope()
			var status by remember { mutableStateOf("Version ${BuildConfig.VERSION_NAME}. Press to check the server for a newer version.") }
			var busy by remember { mutableStateOf(false) }
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_refresh), contentDescription = null) },
				headingContent = { Text("Check for updates") },
				captionContent = { Text(status) },
				enabled = !busy,
				onClick = {
					busy = true
					status = "Checking the server…"
					scope.launch {
						status = when (val r = UpdateChecker.check(context, api.baseUrl, force = true)) {
							UpdateChecker.Result.UpToDate -> "You have the newest version (${BuildConfig.VERSION_NAME})."
							UpdateChecker.Result.NoServer -> "Sign in to a server first."
							UpdateChecker.Result.NeedsPermission -> "Allow Jelly Cinema to install apps, then press this again."
							is UpdateChecker.Result.Installing -> "Installing ${r.versionName}. Choose Update when the TV asks."
							is UpdateChecker.Result.Failed -> "Couldn't update: ${r.reason}"
							UpdateChecker.Result.Skipped -> "Try again in a moment."
						}
						busy = false
					}
				},
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_jellyfin), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_about_title)) },
				onClick = { router.push(Routes.ABOUT) },
				modifier = Modifier.focusKey(Routes.ABOUT),
			)
		}
	}
}
