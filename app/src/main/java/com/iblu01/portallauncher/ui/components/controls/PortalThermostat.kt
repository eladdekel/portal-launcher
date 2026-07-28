package com.iblu01.portallauncher.ui.components.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Thermostat operating mode. [HEAT_COOL] is Apple's "Auto" — a low/high range with two handles. */
enum class ThermostatMode { OFF, HEAT, COOL, HEAT_COOL }

// The C-shaped track: a 270° sweep starting bottom-left, leaving a gap at the bottom.
private const val ARC_START = 135f
private const val ARC_SWEEP = 270f

/**
 * Apple Home-style thermostat dial. A C-shaped arc with a draggable handle per setpoint:
 * one for [HEAT] / [COOL], two for [HEAT_COOL] (drag either end of the range). The arc fills
 * warm→cool between them; [OFF] shows a bare track and ignores touch. Everything is adaptive —
 * pass your own [heatColor] / [coolColor] / [trackColor].
 *
 * @param target setpoint for [HEAT] and [COOL].
 * @param lowTarget/[highTarget] the range ends for [HEAT_COOL].
 * @param current optional live temperature, marked as a dot on the track.
 * @param onCommit fired on release, for pushing the value to a backend.
 */
@Composable
fun ThermostatArc(
    mode: ThermostatMode,
    target: Float,
    onTargetChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    lowTarget: Float = target,
    highTarget: Float = target,
    onRangeChange: (Float, Float) -> Unit = { _, _ -> },
    valueRange: ClosedFloatingPointRange<Float> = 45f..85f,
    step: Float = 1f,
    current: Float? = null,
    unit: String = "°",
    heatColor: Color = Color(0xFFFF9F0A),
    coolColor: Color = Color(0xFF0A84FF),
    trackColor: Color = Color.White.copy(alpha = 0.13f),
    onCommit: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val strokeDp = 26.dp
    val handleDp = 18.dp
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-3f)
    val minGap = step.coerceAtLeast(1f)

    fun toFrac(v: Float) = ((v - valueRange.start) / span).coerceIn(0f, 1f)
    fun toValue(frac: Float): Float {
        val raw = valueRange.start + frac * span
        return (((raw - valueRange.start) / step).roundToInt() * step + valueRange.start)
            .coerceIn(valueRange.start, valueRange.endInclusive)
    }

    // Live status derived from the room temperature vs the setpoint(s).
    val heating = current != null && when (mode) {
        ThermostatMode.HEAT -> current < target
        ThermostatMode.HEAT_COOL -> current < lowTarget
        else -> false
    }
    val cooling = current != null && when (mode) {
        ThermostatMode.COOL -> current > target
        ThermostatMode.HEAT_COOL -> current > highTarget
        else -> false
    }
    // Setpoint reached: the system is calling for nothing → dim the fill a touch.
    val settled = mode != ThermostatMode.OFF && current != null && !heating && !cooling
    val fillAlpha = if (settled) 0.4f else 1f

    // Which handle is being dragged: 0 = low/target, 1 = high. -1 = none.
    var active by remember { mutableIntStateOf(-1) }
    // Read the latest setpoints inside the (never-restarted) gesture without re-keying it —
    // keying pointerInput on the values would cancel the drag on every step.
    val lowState by rememberUpdatedState(lowTarget)
    val highState by rememberUpdatedState(highTarget)
    val onTarget by rememberUpdatedState(onTargetChange)
    val onRange by rememberUpdatedState(onRangeChange)
    val onCommitState by rememberUpdatedState(onCommit)

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(mode, valueRange, step) {
                    if (mode == ThermostatMode.OFF) return@pointerInput

                    val strokePx = strokeDp.toPx()
                    val handlePx = handleDp.toPx()

                    fun fracAt(pos: Offset): Float {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val deg = Math.toDegrees(
                            atan2((pos.y - center.y).toDouble(), (pos.x - center.x).toDouble()),
                        ).toFloat()
                        var a = ((deg - ARC_START) % 360f + 360f) % 360f
                        if (a > ARC_SWEEP) a = if (a - ARC_SWEEP < 360f - a) ARC_SWEEP else 0f
                        return (a / ARC_SWEEP).coerceIn(0f, 1f)
                    }

                    fun apply(handle: Int, frac: Float) {
                        val v = toValue(frac)
                        when (mode) {
                            ThermostatMode.HEAT, ThermostatMode.COOL -> onTarget(v)
                            ThermostatMode.HEAT_COOL ->
                                if (handle == 0) onRange(min(v, highState - minGap), highState)
                                else onRange(lowState, max(v, lowState + minGap))
                            ThermostatMode.OFF -> Unit
                        }
                    }

                    detectDragGestures(
                        onDragStart = { pos ->
                            val f = fracAt(pos)
                            active = if (mode == ThermostatMode.HEAT_COOL) {
                                val dLow = kotlin.math.abs(f - toFrac(lowState))
                                val dHigh = kotlin.math.abs(f - toFrac(highState))
                                if (dLow <= dHigh) 0 else 1
                            } else 0
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            apply(active, f)
                        },
                        onDrag = { change, _ -> if (active >= 0) apply(active, fracAt(change.position)) },
                        onDragEnd = { active = -1; onCommitState() },
                        onDragCancel = { active = -1 },
                    )
                },
        ) {
            val strokePx = strokeDp.toPx()
            val handlePx = handleDp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f - max(handlePx, strokePx / 2f) - 2.dp.toPx()
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            fun pointAt(frac: Float): Offset {
                val rad = Math.toRadians((ARC_START + frac * ARC_SWEEP).toDouble())
                return Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
            }

            fun segment(f0: Float, f1: Float, color: Color, cap: StrokeCap) {
                drawArc(
                    color = color,
                    startAngle = ARC_START + f0 * ARC_SWEEP,
                    sweepAngle = (f1 - f0) * ARC_SWEEP,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = cap),
                )
            }

            // Bare track.
            segment(0f, 1f, trackColor, StrokeCap.Round)

            // Coloured fill per mode (dimmed once the setpoint is satisfied).
            when (mode) {
                ThermostatMode.HEAT -> segment(0f, toFrac(target), heatColor.copy(alpha = fillAlpha), StrokeCap.Round)
                ThermostatMode.COOL -> segment(toFrac(target), 1f, coolColor.copy(alpha = fillAlpha), StrokeCap.Round)
                ThermostatMode.HEAT_COOL -> {
                    val fl = toFrac(lowTarget)
                    val fh = toFrac(highTarget)
                    val n = 48
                    for (i in 0 until n) {
                        val t0 = i / n.toFloat()
                        val t1 = (i + 1) / n.toFloat()
                        val cap = if (i == 0 || i == n - 1) StrokeCap.Round else StrokeCap.Butt
                        segment(fl + (fh - fl) * t0, fl + (fh - fl) * t1, lerp(coolColor, heatColor, t0).copy(alpha = fillAlpha), cap)
                    }
                }
                ThermostatMode.OFF -> Unit
            }

            // Live-temperature marker.
            current?.let { drawCircle(AppleColors.primary.copy(alpha = 0.85f), 4.dp.toPx(), pointAt(toFrac(it))) }

            // Handles.
            fun handle(frac: Float) {
                val p = pointAt(frac)
                drawCircle(Color.Black.copy(alpha = 0.22f), handlePx + 3.dp.toPx(), p + Offset(0f, 2.5.dp.toPx()))
                drawCircle(Color.White, handlePx, p)
            }
            when (mode) {
                ThermostatMode.HEAT, ThermostatMode.COOL -> handle(toFrac(target))
                ThermostatMode.HEAT_COOL -> { handle(toFrac(lowTarget)); handle(toFrac(highTarget)) }
                ThermostatMode.OFF -> Unit
            }
        }

        // Centre readout.
        val statusText: String?
        val statusColor: Color
        when {
            mode == ThermostatMode.OFF -> { statusText = null; statusColor = AppleColors.secondary }
            heating -> { statusText = "Chauffage en cours"; statusColor = heatColor }
            cooling -> { statusText = "Refroidissement en cours"; statusColor = coolColor }
            settled -> { statusText = "Température atteinte"; statusColor = AppleColors.secondary }
            mode == ThermostatMode.HEAT_COOL -> { statusText = "MAINTENIR ENTRE"; statusColor = AppleColors.tertiary }
            else -> { statusText = null; statusColor = AppleColors.tertiary }
        }

        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (statusText != null) {
                Text(
                    statusText,
                    style = AppleTypography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = statusColor,
                )
                Spacer(Modifier.height(6.dp))
            }
            when (mode) {
                ThermostatMode.OFF -> Text(
                    "Éteint",
                    style = AppleTypography.headlineLarge,
                    color = AppleColors.secondary,
                )
                ThermostatMode.HEAT_COOL -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${lowTarget.roundToInt()}",
                        style = AppleTypography.displayLarge.copy(fontSize = 60.sp, fontWeight = FontWeight.Medium),
                        color = AppleColors.primary,
                    )
                    Text(
                        "  –  ",
                        style = AppleTypography.displayLarge.copy(fontSize = 44.sp),
                        color = AppleColors.tertiary,
                    )
                    Text(
                        "${highTarget.roundToInt()}",
                        style = AppleTypography.displayLarge.copy(fontSize = 60.sp, fontWeight = FontWeight.Medium),
                        color = AppleColors.primary,
                    )
                }
                else -> Text(
                    "${target.roundToInt()}$unit",
                    style = AppleTypography.displayLarge.copy(fontSize = 76.sp, fontWeight = FontWeight.Medium),
                    color = AppleColors.primary,
                )
            }
            // Room temperature, when known.
            if (current != null && mode != ThermostatMode.OFF) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Pièce · ${current.roundToInt()}$unit",
                    style = AppleTypography.bodyLarge,
                    color = AppleColors.secondary,
                )
            }
        }
    }
}
