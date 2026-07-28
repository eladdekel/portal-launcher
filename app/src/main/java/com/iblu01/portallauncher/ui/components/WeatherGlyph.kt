package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.PortalTheme
import kotlin.math.sin

enum class Precip { NONE, RAIN, SNOW, THUNDER, FOG }

data class WeatherGlyph(
    val night: Boolean = false,
    val precip: Precip = Precip.NONE,
    val showCloud: Boolean = true,
    val showLuminary: Boolean = true,
)

@Composable
fun WeatherIcon(glyph: WeatherGlyph, modifier: Modifier = Modifier) {
    val ani = rememberGlyphAnimations()

    Canvas(modifier = modifier) {
        val u = size.minDimension
        if (glyph.showLuminary) {
            val lumCenter = if (glyph.showCloud) Offset(size.width * 0.40f, size.height * 0.38f) else center
            if (glyph.night) drawMoon(lumCenter, u * 0.20f) else drawSun(lumCenter, u * 0.16f, ani.rays)
        }
        if (glyph.showCloud) {
            val cloudColor = if (glyph.precip == Precip.FOG) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.92f)
            drawCloud(Offset(size.width * 0.54f, size.height * 0.56f + ani.bob * u * 0.02f), u * 0.34f, cloudColor)
        }
        when (glyph.precip) {
            Precip.RAIN -> drawDrops(u, ani.fall, AppleColors.accent)
            Precip.SNOW -> drawFlakes(u, ani.fall, Color.White)
            Precip.THUNDER -> drawBolt(u, ani.flash)
            Precip.FOG -> drawFog(u, ani.bob)
            Precip.NONE -> Unit
        }
    }
}

/* ── Drawing ──────────────────────────────────────────────────────────────── */

private fun DrawScope.drawSun(c: Offset, r: Float, angleDeg: Float) {
    rotate(angleDeg, pivot = c) {
        for (i in 0 until 8) {
            rotate(i * 45f, pivot = c) {
                drawLine(
                    AppleColors.warning,
                    Offset(c.x, c.y - r * 1.5f),
                    Offset(c.x, c.y - r * 2.1f),
                    strokeWidth = r * 0.22f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
    drawCircle(AppleColors.warning, radius = r, center = c)
}

private fun DrawScope.drawMoon(c: Offset, r: Float) {
    val pale = Color(0xFFEFF3FF)
    drawCircle(pale, radius = r, center = c)
    drawCircle(AppleColors.background, radius = r * 0.85f, center = Offset(c.x + r * 0.5f, c.y - r * 0.35f))
}

private fun DrawScope.drawCloud(c: Offset, r: Float, color: Color) {
    drawCircle(color, radius = r * 0.62f, center = Offset(c.x - r * 0.55f, c.y + r * 0.15f))
    drawCircle(color, radius = r * 0.85f, center = Offset(c.x, c.y - r * 0.1f))
    drawCircle(color, radius = r * 0.6f, center = Offset(c.x + r * 0.6f, c.y + r * 0.15f))
    val left = c.x - r * 1.15f
    val top = c.y + r * 0.15f
    val w = r * 2.3f
    val h = r * 0.62f
    drawRoundRect(color, Offset(left, top), Size(w, h), CornerRadius(h / 2f, h / 2f))
}

private fun DrawScope.drawDrops(u: Float, t: Float, color: Color) {
    val xs = listOf(0.4f, 0.55f, 0.68f)
    xs.forEachIndexed { i, fx ->
        val phase = (t + i * 0.33f) % 1f
        val x = size.width * fx
        val y = size.height * (0.72f + phase * 0.22f)
        drawLine(color, Offset(x, y), Offset(x, y + u * 0.09f), strokeWidth = u * 0.03f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawFlakes(u: Float, t: Float, color: Color) {
    val xs = listOf(0.42f, 0.56f, 0.7f)
    xs.forEachIndexed { i, fx ->
        val phase = (t + i * 0.33f) % 1f
        val x = size.width * fx
        val y = size.height * (0.74f + phase * 0.2f)
        drawCircle(color, radius = u * 0.028f, center = Offset(x, y))
    }
}

private fun DrawScope.drawBolt(u: Float, flash: Float) {
    val alpha = 0.55f + 0.45f * sin(flash * 6.28f).coerceAtLeast(0f)
    val path = Path().apply {
        moveTo(size.width * 0.56f, size.height * 0.66f)
        lineTo(size.width * 0.47f, size.height * 0.86f)
        lineTo(size.width * 0.55f, size.height * 0.86f)
        lineTo(size.width * 0.48f, size.height * 1.02f)
        lineTo(size.width * 0.66f, size.height * 0.80f)
        lineTo(size.width * 0.57f, size.height * 0.80f)
        lineTo(size.width * 0.63f, size.height * 0.66f)
        close()
    }
    drawPath(path, AppleColors.warning.copy(alpha = alpha))
}

private fun DrawScope.drawFog(u: Float, bob: Float) {
    val color = Color.White.copy(alpha = 0.5f)
    for (i in 0 until 3) {
        val y = size.height * (0.74f + i * 0.09f)
        val dx = bob * u * 0.05f * (if (i % 2 == 0) 1f else -1f)
        drawLine(color, Offset(size.width * 0.32f + dx, y), Offset(size.width * 0.72f + dx, y), strokeWidth = u * 0.03f, cap = StrokeCap.Round)
    }
}

/* ── Animation ────────────────────────────────────────────────────────────── */

private data class GlyphAnimations(val rays: Float, val fall: Float, val flash: Float, val bob: Float)

@Composable
private fun rememberGlyphAnimations(): GlyphAnimations {
    val t = rememberInfiniteTransition()
    val rays by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "rays")
    val fall by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "fall")
    val flash by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "flash")
    val wobble by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "wobble")
    return GlyphAnimations(rays, fall, flash, sin(wobble))
}

@Preview(backgroundColor = 0xFF05070A, showBackground = true, widthDp = 320, heightDp = 90)
@Composable
private fun WeatherGlyphPreview() {
    PortalTheme {
        Row {
            listOf(
                WeatherGlyph(precip = Precip.NONE, showCloud = false, showLuminary = true, night = false),
                WeatherGlyph(precip = Precip.NONE, showCloud = true, showLuminary = true, night = false),
                WeatherGlyph(precip = Precip.NONE, showCloud = true, showLuminary = false),
                WeatherGlyph(precip = Precip.RAIN, showCloud = true, showLuminary = false),
                WeatherGlyph(precip = Precip.SNOW, showCloud = true, showLuminary = false),
                WeatherGlyph(precip = Precip.THUNDER, showCloud = true, showLuminary = false),
            ).forEach {
                WeatherIcon(it, Modifier.size(72.dp))
            }
        }
    }
}
