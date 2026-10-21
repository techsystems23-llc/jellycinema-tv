package org.jellyfin.androidtv.jellycinema

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Seasonal falling effects over the whole Home screen, the same themes and calendar as the
 * Jelly Cinema web version (leaves and bats for Halloween, snow for Christmas...). Drawn by the app itself.
 */
object SeasonalTheme {
	private fun md(m: Int, d: Int, y: Int) = LocalDate.of(y, m, d)
	private fun thanksgiving(y: Int) = LocalDate.of(y, 11, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY))
	private fun easter(y: Int): LocalDate {
		val a = y % 19; val b = y / 100; val c = y % 100; val d = b / 4; val e = b % 4
		val f = (b + 8) / 25; val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
		val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
		val month = (h + l - 7 * m + 114) / 31; val day = ((h + l - 7 * m + 114) % 31) + 1
		return LocalDate.of(y, month, day)
	}

	fun current(today: LocalDate = LocalDate.now()): String {
		val y = today.year
		fun inR(f: LocalDate, t: LocalDate) = !today.isBefore(f) && !today.isAfter(t)
		val cal = listOf(
			"easter" to (easter(y).minusDays(7) to easter(y)),
			"newyear" to (md(1, 1, y) to md(1, 2, y)),
			"winter" to (md(1, 3, y) to md(1, 31, y)),
			"valentines" to (md(2, 7, y) to md(2, 14, y)),
			"stpatricks" to (md(3, 14, y) to md(3, 17, y)),
			"spring" to (md(3, 20, y) to md(4, 30, y)),
			"summer" to (md(6, 10, y) to md(6, 27, y)),
			"july4" to (md(6, 28, y) to md(7, 5, y)),
			"fall" to (md(9, 22, y) to md(10, 14, y)),
			"halloween" to (md(10, 15, y) to md(10, 31, y)),
			"thanksgiving" to (thanksgiving(y).minusDays(6) to thanksgiving(y)),
			"christmas" to (thanksgiving(y).plusDays(1) to md(12, 26, y)),
			"newyear" to (md(12, 27, y) to md(12, 31, y)),
		)
		return cal.firstOrNull { (_, r) -> inR(r.first, r.second) }?.first ?: "plain"
	}

	/** Particle kinds and their share of the particle count. */
	fun parts(theme: String): List<Pair<String, Float>> = when (theme) {
		"newyear" -> listOf("sparkle" to 1f)
		"winter", "christmas" -> listOf("snow" to 1f)
		"valentines" -> listOf("heart" to 0.6f)
		"stpatricks" -> listOf("clover" to 0.6f)
		"spring" -> listOf("petal" to 0.8f)
		"easter" -> listOf("pastelpetal" to 0.6f, "egg" to 0.25f)
		"summer" -> listOf("firefly" to 0.7f)
		"july4" -> listOf("star" to 0.6f)
		"fall" -> listOf("leaf" to 0.6f)
		"halloween" -> listOf("leaf" to 0.45f, "bat" to 0.12f)
		"thanksgiving" -> listOf("tleaf" to 0.55f)
		else -> emptyList()
	}
}

private val PAL = mapOf(
	"sparkle" to listOf(0xFFFFD700, 0xFFFFE680, 0xFFF5C542, 0xFFFFF3B0),
	"heart" to listOf(0xFFFF4D6D, 0xFFFF8FA3, 0xFFE63946, 0xFFFFB3C1),
	"clover" to listOf(0xFF2E9E44, 0xFF3CB043, 0xFF1F7A35),
	"petal" to listOf(0xFFFFC0CB, 0xFFFFB7C5, 0xFFF8A5C2, 0xFFFFD1DC),
	"pastelpetal" to listOf(0xFFFFD1DC, 0xFFC9F2C7, 0xFFCDE7FF, 0xFFFFF2B2, 0xFFE3D1FF),
	"egg" to listOf(0xFFFFD1DC, 0xFFC9F2C7, 0xFFCDE7FF, 0xFFFFF2B2, 0xFFE3D1FF),
	"star" to listOf(0xFFE63946, 0xFFFFFFFF, 0xFF1D4ED8),
	"leaf" to listOf(0xFFE3A008, 0xFFF2B705, 0xFFD97706, 0xFFC2410C, 0xFFEAB308),
	"tleaf" to listOf(0xFFB45309, 0xFF9A3412, 0xFFC2410C, 0xFF78350F, 0xFFD97706),
).mapValues { (_, v) -> v.map { Color(it) } }

private class P(val kind: String) {
	var x = 0f; var y = 0f; var rot = 0f; var vr = 0f; var t = 0f
	var r = 0f; var vx = 0f; var vy = 0f; var a = 1f; var tw = 1f; var flap = 9f
	var c: Color = Color.White; var c2: Color = Color.White
}

private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

// sizes are in the web version's CSS pixels; [u] converts them to this screen (1 web px = 1 dp)
private fun make(kind: String, w: Float, h: Float, u: Float, init: Boolean): P = P(kind).apply {
	x = rnd(0f, w); y = if (init) rnd(-h, h) else rnd(-60f, -10f) * u; rot = rnd(0f, 6.28f); vr = rnd(-1f, 1f); t = rnd(0f, 100f)
	fun col() = PAL[kind]?.random() ?: Color.White
	when (kind) {
		"snow" -> { r = rnd(1.2f, 3.6f); vy = rnd(20f, 55f); vx = rnd(-8f, 8f); a = rnd(0.5f, 0.95f) }
		"sparkle" -> { if (init) y = rnd(0f, h); r = rnd(3f, 8f); vy = rnd(8f, 22f); vx = rnd(-5f, 5f); c = col(); tw = rnd(1.5f, 4f) }
		"heart" -> { r = rnd(7f, 14f); vy = rnd(25f, 50f); vx = rnd(-10f, 10f); c = col(); vr = rnd(-0.6f, 0.6f) }
		"clover" -> { r = rnd(7f, 13f); vy = rnd(25f, 50f); vx = rnd(-10f, 10f); c = col() }
		"petal", "pastelpetal" -> { r = rnd(5f, 10f); vy = rnd(25f, 55f); vx = rnd(10f, 35f); c = col(); vr = rnd(-2f, 2f) }
		"egg" -> { r = rnd(8f, 13f); vy = rnd(30f, 50f); vx = rnd(-6f, 6f); c = col(); c2 = col(); vr = rnd(-0.8f, 0.8f) }
		"firefly" -> { y = rnd(h * 0.25f, h); r = rnd(1.6f, 3f); vx = rnd(-12f, 12f); vy = rnd(-12f, 12f); tw = rnd(0.6f, 1.6f) }
		"star" -> { r = rnd(5f, 11f); vy = rnd(20f, 45f); vx = rnd(-8f, 8f); c = col(); vr = rnd(-1.2f, 1.2f) }
		"leaf", "tleaf" -> { r = rnd(8f, 15f); vy = rnd(30f, 60f); vx = rnd(-15f, 15f); c = col(); vr = rnd(-2f, 2f) }
		"bat" -> {
			val dir = if (Random.nextBoolean()) 1f else -1f
			x = if (init) rnd(0f, w) else if (dir > 0) -40f * u else w + 40f * u
			y = rnd(h * 0.05f, h * 0.6f); r = rnd(10f, 18f); vx = dir * rnd(60f, 120f); vy = rnd(-10f, 10f); flap = rnd(7f, 11f)
		}
	}
	r *= u; vx *= u; vy *= u
}

private fun step(p: P, dt: Float, w: Float, h: Float, u: Float): Boolean {
	p.t += dt
	when (p.kind) {
		"firefly" -> {
			p.vx = (p.vx + rnd(-20f, 20f) * u * dt).coerceIn(-18f * u, 18f * u)
			p.vy = (p.vy + rnd(-20f, 20f) * u * dt).coerceIn(-18f * u, 18f * u)
			p.x += p.vx * dt; p.y += p.vy * dt
			if (p.x < -10 * u) p.x = w + 10 * u; if (p.x > w + 10 * u) p.x = -10 * u
			if (p.y < h * 0.15f) p.vy = abs(p.vy); if (p.y > h + 10 * u) p.y = h * 0.4f
			return true
		}
		"bat" -> {
			p.x += p.vx * dt; p.y += (p.vy + sin(p.t * 2) * 25 * u) * dt
			return p.x > -60 * u && p.x < w + 60 * u
		}
	}
	p.x += (p.vx + sin(p.t * 1.3f) * 12 * u) * dt; p.y += p.vy * dt; p.rot += p.vr * dt
	if (p.x < -40 * u) p.x = w + 30 * u else if (p.x > w + 40 * u) p.x = -30 * u
	return p.y < h + 40 * u
}

private fun heartPath(r: Float) = Path().apply {
	moveTo(0f, r * 0.35f)
	cubicTo(-r, -r * 0.3f, -r * 0.45f, -r * 1.05f, 0f, -r * 0.45f)
	cubicTo(r * 0.45f, -r * 1.05f, r, -r * 0.3f, 0f, r * 0.35f)
	close()
}

private fun starPath(r: Float, n: Int, inner: Float) = Path().apply {
	for (i in 0 until n * 2) {
		val rr = if (i % 2 == 1) r * inner else r
		val a = (i * PI / n - PI / 2).toFloat()
		if (i == 0) moveTo(cos(a) * rr, sin(a) * rr) else lineTo(cos(a) * rr, sin(a) * rr)
	}
	close()
}

private val batColor = Color(0xF223122D)
private val batGlow = Color(0x55FF8C28)

private fun DrawScope.drawBat(r: Float, t: Float, flap: Float) {
	val w = sin(t * flap) * 0.6f
	val body = Path().apply { addOval(androidx.compose.ui.geometry.Rect(-r * 0.22f, -r * 0.32f, r * 0.22f, r * 0.32f)) }
	val wings = Path()
	for (s in listOf(-1f, 1f)) {
		wings.moveTo(0f, -r * 0.1f)
		wings.quadraticTo(s * r * 0.6f, -r * (0.6f + w), s * r * 1.3f, -r * (0.15f + w))
		wings.quadraticTo(s * r * 1.0f, r * 0.05f, s * r * 0.75f, r * 0.12f)
		wings.quadraticTo(s * r * 0.5f, -r * 0.05f, s * r * 0.35f, r * 0.2f)
		wings.quadraticTo(s * r * 0.2f, 0f, 0f, r * 0.1f)
		wings.close()
	}
	val ears = Path().apply {
		moveTo(-r * 0.15f, -r * 0.25f); lineTo(-r * 0.08f, -r * 0.48f); lineTo(0f, -r * 0.28f)
		lineTo(r * 0.08f, -r * 0.48f); lineTo(r * 0.15f, -r * 0.25f); close()
	}
	// soft orange glow behind, like the web version's shadow
	scale(1.15f, pivot = Offset.Zero) { drawPath(wings, batGlow); drawPath(body, batGlow) }
	drawPath(wings, batColor); drawPath(body, batColor); drawPath(ears, batColor)
}

private fun DrawScope.drawP(p: P) {
	translate(p.x, p.y) {
		rotate(Math.toDegrees(p.rot.toDouble()).toFloat(), pivot = Offset.Zero) {
			val r = p.r
			when (p.kind) {
				"snow" -> drawCircle(Color.White.copy(alpha = p.a), r, Offset.Zero)
				"sparkle" -> {
					val al = 0.35f + 0.65f * abs(sin(p.t * p.tw))
					drawCircle(p.c.copy(alpha = al * 0.25f), r * 1.6f, Offset.Zero)
					drawPath(starPath(r, 4, 0.3f), p.c.copy(alpha = al))
				}
				"heart" -> drawPath(heartPath(r), p.c.copy(alpha = 0.8f))
				"clover" -> {
					for (i in 0 until 4) rotate(i * 90f, pivot = Offset.Zero) {
						translate(0f, -r * 0.45f) { drawPath(heartPath(r * 0.55f), p.c.copy(alpha = 0.85f)) }
					}
					drawPath(Path().apply { moveTo(0f, 0f); quadraticTo(r * 0.3f, r * 0.7f, r * 0.1f, r * 1.1f) }, p.c, style = Stroke(2f))
				}
				"petal", "pastelpetal" -> scale(1f, 0.55f + 0.45f * abs(sin(p.t * 2)), pivot = Offset.Zero) {
					drawOval(p.c.copy(alpha = 0.85f), Offset(-r, -r * 0.6f), Size(r * 2, r * 1.2f))
				}
				"egg" -> {
					drawOval(p.c.copy(alpha = 0.9f), Offset(-r * 0.75f, -r), Size(r * 1.5f, r * 2))
					drawPath(Path().apply { moveTo(-r * 0.7f, 0f); quadraticTo(0f, -r * 0.25f, r * 0.7f, 0f) }, p.c2, style = Stroke(2.5f))
					drawPath(Path().apply { moveTo(-r * 0.6f, r * 0.4f); quadraticTo(0f, r * 0.15f, r * 0.6f, r * 0.4f) }, p.c2, style = Stroke(2.5f))
				}
				"firefly" -> {
					val g = 0.25f + 0.75f * max(0f, sin(p.t * p.tw))
					drawCircle(Color(0xFFD4FF3A).copy(alpha = g * 0.3f), r * 3f, Offset.Zero)
					drawCircle(Color(0xFFE8FF7A).copy(alpha = g), r, Offset.Zero)
				}
				"star" -> drawPath(starPath(r, 5, 0.45f), p.c.copy(alpha = 0.85f))
				"leaf", "tleaf" -> {
					val leaf = Path().apply {
						moveTo(0f, -r); quadraticTo(r * 0.9f, -r * 0.3f, 0f, r); quadraticTo(-r * 0.9f, -r * 0.3f, 0f, -r); close()
					}
					drawPath(leaf, p.c.copy(alpha = 0.85f))
					drawLine(Color(0x73502800), Offset(0f, -r * 0.85f), Offset(0f, r * 1.25f), 1f)
				}
				"bat" -> rotate(-Math.toDegrees(p.rot.toDouble()).toFloat(), pivot = Offset.Zero) { drawBat(r, p.t, p.flap) }
			}
		}
	}
}

/** Full-screen falling effect for today's theme (draws nothing on "plain" days). Never takes focus or input. */
@Composable
fun SeasonalEffects(modifier: Modifier = Modifier, theme: String = remember { SeasonalTheme.current() }) {
	val parts = remember(theme) { SeasonalTheme.parts(theme) }
	if (parts.isEmpty()) return
	val particles = remember(theme) { ArrayList<P>() }
	var frame by remember { mutableLongStateOf(0L) }
	var size by remember { androidx.compose.runtime.mutableStateOf(Size.Zero) }
	var unit by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }

	LaunchedEffect(theme) {
		var last = 0L
		while (true) {
			withFrameNanos { now ->
				val dt = if (last == 0L) 0f else min(0.05f, (now - last) / 1_000_000_000f)
				last = now
				val w = size.width; val h = size.height
				if (w > 0 && h > 0) {
					if (particles.isEmpty()) {
						// same density as the web version: one particle per 22,000 square web pixels, 18 to 90
						val webArea = (w / unit) * (h / unit)
						val base = (webArea / 22000f).roundToInt().coerceIn(18, 90)
						for ((kind, share) in parts) repeat((base * share).roundToInt()) { particles.add(make(kind, w, h, unit, true)) }
					}
					for (i in particles.indices) {
						if (!step(particles[i], dt, w, h, unit)) particles[i] = make(particles[i].kind, w, h, unit, false)
					}
				}
				frame = now
			}
		}
	}

	Canvas(modifier.fillMaxSize()) {
		if (size != this.size) { size = this.size; unit = density; particles.clear() }
		@Suppress("UNUSED_EXPRESSION") frame
		for (p in particles) drawP(p)
	}
}
