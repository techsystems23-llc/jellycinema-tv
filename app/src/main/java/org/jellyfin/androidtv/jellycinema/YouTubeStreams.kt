package org.jellyfin.androidtv.jellycinema

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

/**
 * Turns a YouTube trailer into a plain video address the app's own player can show inside the banner.
 * (YouTube's web player in a WebView is hidden behind the app on many TVs, so it is only a fallback.)
 */
object YouTubeStreams {
	private val client = OkHttpClient.Builder()
		.connectTimeout(10, TimeUnit.SECONDS)
		.readTimeout(20, TimeUnit.SECONDS)
		.build()

	@Volatile private var ready = false

	private fun init() {
		if (ready) return
		synchronized(this) {
			if (ready) return
			NewPipe.init(OkDownloader(client), Localization("en", "US"))
			ready = true
		}
	}

	/** A direct video address (no sound needed: the banner is muted), best up to 1080p, or null. */
	suspend fun videoUrl(id: String): String? = withContext(Dispatchers.IO) {
		withTimeoutOrNull(20_000) {
			runCatching {
				init()
				val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
				fun height(res: String?) = res?.substringBefore('p')?.toIntOrNull() ?: 0
				val videoOnly = info.videoOnlyStreams
					.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
					.filter { (it.format?.mimeType ?: "").contains("mp4") }
					.filter { height(it.resolution) in 1..1080 }
					.maxByOrNull { height(it.resolution) }
				val muxed = info.videoStreams
					.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
					.maxByOrNull { height(it.resolution) }
				(videoOnly ?: muxed)?.content
			}.onFailure { JellyCinemaDiagnostics.trailer("YouTube $id: ${it.javaClass.simpleName} ${it.message ?: ""}".take(160)) }
				.getOrNull()
		}
	}

	private class OkDownloader(private val client: OkHttpClient) : Downloader() {
		override fun execute(request: Request): Response {
			val body = request.dataToSend()?.toRequestBody()
			val builder = okhttp3.Request.Builder().url(request.url()).method(request.httpMethod(), body)
				.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
			request.headers().forEach { (name, values) ->
				builder.removeHeader(name)
				values.forEach { builder.addHeader(name, it) }
			}
			client.newCall(builder.build()).execute().use { r ->
				val text = r.body?.string()
				return Response(r.code, r.message, r.headers.toMultimap(), text, r.request.url.toString())
			}
		}
	}
}
