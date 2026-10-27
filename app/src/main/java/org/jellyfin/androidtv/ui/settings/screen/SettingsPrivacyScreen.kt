package org.jellyfin.androidtv.ui.settings.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn

/** The full privacy policy, published by TechSystems23 LLC. */
const val PRIVACY_POLICY_URL = "https://techsystems23.com/apps/jelly-cinema/privacy/"

/** Settings → About → Privacy policy: a short summary and a QR code for the full policy (TVs usually have no web browser). */
@Composable
fun SettingsPrivacyScreen() {
	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.app_name).uppercase()) },
				headingContent = { Text(stringResource(R.string.jc_privacy_title)) },
				captionContent = { Text(stringResource(R.string.jc_privacy_text)) },
			)
		}
		item {
			ListSection(
				headingContent = { Text(stringResource(R.string.jc_privacy_scan)) },
				captionContent = { Text(PRIVACY_POLICY_URL) },
			)
		}
		item {
			Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
				QrCode(PRIVACY_POLICY_URL, stringResource(R.string.jc_privacy_scan))
			}
		}
	}
}
