package org.jellyfin.androidtv.jellycinema

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.leanback.widget.Presenter
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.util.getActivity
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject
import java.util.UUID

/**
 * Top 10 card, sized like Netflix's TV Top 10 row: a big outlined rank number with the poster beside it,
 * about five and a half titles across a 1080p screen.
 * [ranks] maps item id to its place (1-10) in the row.
 */
class Top10CardPresenter(private val ranks: Map<UUID, Int>) : Presenter() {
	companion object {
		val POSTER_HEIGHT = 110.dp
		val POSTER_WIDTH = 73.dp     // 2:3
		val NUMBER_WIDTH = 78.dp
		val OVERLAP = 12.dp          // the poster just touches the number's right edge
	}

	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
		val view = ComposeView(parent.context).apply {
			setParentCompositionContext(parent.findViewTreeCompositionContext())
			setViewTreeLifecycleOwner(parent.findViewTreeLifecycleOwner())
			setViewTreeSavedStateRegistryOwner(parent.findViewTreeSavedStateRegistryOwner())
			isFocusable = true
			isFocusableInTouchMode = true
			// no grey Android focus box: the poster's white frame shows focus
			if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
			setOnLongClickListener {
				context.getActivity()?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU)) ?: false
			}
		}
		return Top10ViewHolder(view)
	}

	override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
		if (viewHolder !is Top10ViewHolder || item !is BaseRowItem) return
		viewHolder.item.value = item
		viewHolder.focused.value = viewHolder.view.isFocused
	}

	override fun onUnbindViewHolder(viewHolder: ViewHolder) {
		if (viewHolder !is Top10ViewHolder) return
		viewHolder.item.value = null
	}

	private inner class Top10ViewHolder(composeView: ComposeView) : ViewHolder(composeView) {
		val item = MutableStateFlow<BaseRowItem?>(null)
		val focused = MutableStateFlow(false)

		init {
			composeView.setContent {
				val i by item.collectAsState()
				val f by focused.collectAsState()
				Top10Card(i, i?.baseItem?.id?.let { ranks[it] } ?: 0, f)
			}
			composeView.onFocusChangeListener = { _, has -> focused.value = has }
		}
	}
}

private val RankFont = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(org.jellyfin.androidtv.R.font.jc_rank))

@Composable
private fun Top10Card(item: BaseRowItem?, rank: Int, focused: Boolean) {
	val api = koinInject<ApiClient>()
	val base = item?.baseItem
	val overlap = Top10CardPresenter.OVERLAP
	Box(
		modifier = Modifier
			.padding(vertical = 6.dp)
			.width(Top10CardPresenter.NUMBER_WIDTH + Top10CardPresenter.POSTER_WIDTH - overlap)
			.height(Top10CardPresenter.POSTER_HEIGHT),
	) {
		// rank number, Netflix style: heavy numerals, near-black fill with a light outline,
		// standing on the poster's bottom line with the poster tucked slightly over its right edge
		if (rank > 0) {
			val size = if (rank >= 10) 96.sp else 118.sp
			val style = TextStyle(
				fontFamily = RankFont, fontSize = size, lineHeight = size,
				letterSpacing = if (rank >= 10) (-10).sp else 0.sp,
				platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
				lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
					alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Bottom,
					trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
				),
			)
			Box(
				modifier = Modifier
					.align(Alignment.BottomStart)
					.width(Top10CardPresenter.NUMBER_WIDTH)
					.graphicsLayer {
						// a little narrower, like Netflix's numerals; "10" squeezed more so it fits
						scaleX = if (rank >= 10) 0.62f else 0.84f
						transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
					},
				contentAlignment = Alignment.BottomEnd,
			) {
				Text(text = rank.toString(), maxLines = 1, softWrap = false,
					style = style.copy(color = Color(0xFFD9D9D9), drawStyle = Stroke(width = 9f, join = androidx.compose.ui.graphics.StrokeJoin.Round)))
				Text(text = rank.toString(), maxLines = 1, softWrap = false,
					style = style.copy(color = Color(0xFF141414)))
			}
		}
		Box(
			modifier = Modifier
				.align(Alignment.BottomEnd)
				.width(Top10CardPresenter.POSTER_WIDTH)
				.height(Top10CardPresenter.POSTER_HEIGHT)
				.shadow(if (focused) 14.dp else 6.dp, RoundedCornerShape(6.dp))
				.clip(RoundedCornerShape(6.dp))
				.background(Color(0xFF1F2937))
				.then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier),
		) {
			val tag = base?.imageTags?.get(ImageType.PRIMARY)
			if (base != null && tag != null) {
				AsyncImage(
					model = api.imageApi.getItemImageUrl(itemId = base.id, imageType = ImageType.PRIMARY, tag = tag, maxHeight = 360, quality = 90),
					contentDescription = base.name,
					contentScale = ContentScale.Crop,
					modifier = Modifier.fillMaxSize(),
				)
			} else if (base != null) {
				Text(base.name ?: "", modifier = Modifier.align(Alignment.BottomStart).padding(8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis,
					style = TextStyle(fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold))
			}
		}
	}
}
