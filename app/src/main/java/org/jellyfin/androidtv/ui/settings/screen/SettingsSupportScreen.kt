package org.jellyfin.androidtv.ui.settings.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.jellycinema.TipJar
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn

/** True when this build has a way to tip: Google Play tips, or a support page set for the build. */
val supportAvailable: Boolean get() = TipJar.usesStore || BuildConfig.SUPPORT_URL.isNotBlank()

private tailrec fun Context.activity(): Activity? = when (this) {
	is Activity -> this
	is ContextWrapper -> baseContext.activity()
	else -> null
}

/** Settings → Support the developer. Optional tips; they don't unlock anything. */
@Composable
fun SettingsSupportScreen() {
	val context = LocalContext.current
	var tips by remember { mutableStateOf<List<TipJar.Tip>?>(null) }
	var status by remember { mutableStateOf<String?>(null) }
	if (TipJar.usesStore) LaunchedEffect(Unit) { tips = runCatching { TipJar.tips(context) }.getOrDefault(emptyList()) }

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.app_name).uppercase()) },
				headingContent = { Text(stringResource(R.string.jc_support_title)) },
				captionContent = { Text(stringResource(R.string.jc_support_text)) },
			)
		}

		if (TipJar.usesStore) {
			val list = tips
			when {
				list == null -> item { ListButton(headingContent = { Text(stringResource(R.string.jc_support_loading)) }, enabled = false, onClick = {}) }
				list.isEmpty() -> item { ListButton(headingContent = { Text(stringResource(R.string.jc_support_unavailable)) }, enabled = false, onClick = {}) }
				else -> list.forEach { tip ->
					item(key = tip.id) {
						ListButton(
							leadingContent = { Icon(painterResource(R.drawable.ic_heart), contentDescription = null) },
							headingContent = { Text(tip.name) },
							captionContent = { Text(tip.price) },
							onClick = {
								val activity = context.activity() ?: return@ListButton
								status = null
								TipJar.buy(activity, tip) { r ->
									status = when (r) {
										TipJar.Result.Thanks -> context.getString(R.string.jc_support_thanks)
										TipJar.Result.Pending -> context.getString(R.string.jc_support_pending)
										TipJar.Result.Cancelled -> null
										TipJar.Result.Failed -> context.getString(R.string.jc_support_failed)
									}
								}
							},
						)
					}
				}
			}
			status?.let { s -> item { ListSection(headingContent = { Text(s) }) } }
		} else if (BuildConfig.SUPPORT_URL.isNotBlank()) {
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
