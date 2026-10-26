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
			// Jelly Cinema: home screen options (banner, trailers, Theater Mode, rows, seasonal effects)
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_photos), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.jc_settings_title)) },
				captionContent = { Text(stringResource(R.string.jc_settings_caption)) },
				onClick = { router.push(Routes.JELLY_CINEMA) },
				modifier = Modifier.focusKey(Routes.JELLY_CINEMA),
			)
		}

		if (BuildConfig.SELF_UPDATE) item {
			// Jelly Cinema: update the app from the Jellyfin server it is connected to
			val context = LocalContext.current
			val api = koinInject<ApiClient>()
			val scope = rememberCoroutineScope()
			var status by remember { mutableStateOf(context.getString(R.string.jc_update_press_to_check, BuildConfig.VERSION_NAME)) }
			var busy by remember { mutableStateOf(false) }
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_refresh), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.jc_update_check)) },
				captionContent = { Text(status) },
				enabled = !busy,
				onClick = {
					busy = true
					status = context.getString(R.string.jc_update_checking)
					scope.launch {
						status = when (val r = UpdateChecker.check(context, api.baseUrl, force = true)) {
							UpdateChecker.Result.UpToDate -> context.getString(R.string.jc_update_up_to_date, BuildConfig.VERSION_NAME)
							UpdateChecker.Result.NoServer -> context.getString(R.string.jc_update_no_server)
							UpdateChecker.Result.NeedsPermission -> context.getString(R.string.jc_update_needs_permission)
							is UpdateChecker.Result.Installing -> context.getString(R.string.jc_update_choose_update, r.versionName)
							is UpdateChecker.Result.Failed -> context.getString(R.string.jc_update_failed, r.reason)
							UpdateChecker.Result.Skipped -> context.getString(R.string.jc_update_try_again)
						}
						busy = false
					}
				},
			)
		}

		if (supportAvailable) item {
			// Jelly Cinema: optional tips (Google Play in the Play build, a QR code elsewhere)
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_heart), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.jc_support_title)) },
				captionContent = { Text(stringResource(R.string.jc_support_caption)) },
				onClick = { router.push(Routes.SUPPORT) },
				modifier = Modifier.focusKey(Routes.SUPPORT),
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
