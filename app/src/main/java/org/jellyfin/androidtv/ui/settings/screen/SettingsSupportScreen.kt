package org.jellyfin.androidtv.ui.settings.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn

/** True when this build has a tip page (the GitHub and server versions; never the Play or Amazon version). */
val supportAvailable: Boolean get() = BuildConfig.SUPPORT_URL.isNotBlank()

/** Settings → Support the developer: a QR code for the tip page. Tips are optional and don't unlock anything. */
@Composable
fun SettingsSupportScreen() {
	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.app_name).uppercase()) },
				headingContent = { Text(stringResource(R.string.jc_support_title)) },
				captionContent = { Text(stringResource(R.string.jc_support_text)) },
			)
		}
		if (supportAvailable) {
			item {
				ListSection(
					headingContent = { Text(stringResource(R.string.jc_support_scan)) },
					captionContent = { Text(BuildConfig.SUPPORT_URL) },
				)
			}
			item {
				Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
					QrCode(BuildConfig.SUPPORT_URL, stringResource(R.string.jc_support_scan))
				}
			}
		}
	}
}

/** A QR code drawn by the app (black on white, with the quiet border phones need to read it). */
@Composable
private fun QrCode(text: String, description: String) {
	val matrix = remember(text) {
		runCatching { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2)) }.getOrNull()
	} ?: return
	Canvas(
		Modifier
			.size(200.dp)
			.background(Color.White)
			.semantics { contentDescription = description }
	) {
		val cell = size.width / matrix.width
		for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
			if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
		}
	}
}
