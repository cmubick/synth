//
//  Knob.kt
//
//  A rotary knob. Drag up/down to turn it, double-tap to reset. While turning,
//  the label shows the value (e.g. "1.2 kHz"). Ported from Knob.swift.
//

package com.tuesdaygames.synth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuesdaygames.synth.engine.ParamInfo
import com.tuesdaygames.synth.model.SynthPatch
import kotlin.math.abs
import kotlin.math.max

@Composable
fun RotaryKnob(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    default: Float,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
    bipolar: Boolean = false,
    scale: Float = 1f,
) {
    val size = LocalKnobSize.current * scale
    var dragging by remember { mutableStateOf(false) }
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onChange)
    val span = range.endInclusive - range.start
    val norm = ((value - range.start) / span).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.semantics {
            contentDescription = label
            stateDescription = format(value)
        },
    ) {
        KnobFace(
            norm = norm,
            bipolar = bipolar,
            active = dragging,
            modifier = Modifier
                .size(size)
                .pointerInput(range, default) {
                    var lastTapUp = 0L
                    // A full sweep takes ~3 knob-heights of finger travel (at least 150 dp).
                    val travel = max(150.dp.toPx(), size.toPx() * 3.2f)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = currentValue
                        var moved = false
                        var totalDy = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (!moved) {
                                    // Double-tap resets.
                                    if (change.uptimeMillis - lastTapUp < 300) {
                                        currentOnChange(default)
                                        lastTapUp = 0
                                    } else {
                                        lastTapUp = change.uptimeMillis
                                    }
                                }
                                break
                            }
                            totalDy += change.position.y - change.previousPosition.y
                            if (!moved && abs(totalDy) > viewConfiguration.touchSlop) {
                                moved = true
                                dragging = true
                            }
                            if (moved) {
                                change.consume()
                                val next = (start - totalDy / travel * span).coerceIn(range.start, range.endInclusive)
                                currentOnChange(next)
                            }
                        }
                        dragging = false
                    }
                },
        )
        Text(
            text = if (dragging) format(value) else label,
            style = Theme.labelStyle(),
            color = if (dragging) Theme.accent else Theme.label,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(size + 16.dp),
        )
    }
}

/** A knob wired to one of the synth's sound parameters. */
@Composable
fun Knob(patch: SynthPatch, id: Int, label: String? = null, scale: Float = 1f) {
    val info = ParamInfo.of(id)
    RotaryKnob(
        label = label ?: info.name,
        value = patch[id],
        range = info.min..info.max,
        default = info.default,
        format = info::format,
        onChange = { patch[id] = it },
        bipolar = info.isBipolar,
        scale = scale,
    )
}

@Composable
fun KnobFace(norm: Float, bipolar: Boolean, active: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = minOf(size.width, size.height)
        val ring = max(2.5.dp.toPx(), s * 0.065f)
        val inset = ring / 2
        val arcSize = Size(s - ring, s - ring)
        val topLeft = Offset(inset, inset)
        // The track runs from bottom-left (135°) clockwise for 270°.
        drawArc(Theme.track, 135f, 270f, false, topLeft, arcSize, style = Stroke(ring, cap = StrokeCap.Round))
        val from = if (bipolar) 0.5f else 0f
        val start = 135f + 270f * minOf(from, norm)
        val sweep = 270f * abs(norm - from)
        if (sweep > 0.01f) {
            drawArc(Theme.accent, start, sweep, false, topLeft, arcSize, style = Stroke(ring, cap = StrokeCap.Round))
        }
        // The knob body.
        val r = s / 2 - s * 0.17f
        drawCircle(Theme.knobGradient, r, center)
        drawCircle(Color.White.copy(alpha = 0.12f), r, center, style = Stroke(1.dp.toPx()))
        // The pointer.
        rotate(-135f + 270f * norm) {
            val w = max(2.dp.toPx(), s * 0.055f)
            drawLine(
                Theme.cream,
                Offset(center.x, center.y - s * 0.31f),
                Offset(center.x, center.y - s * 0.09f),
                strokeWidth = w,
                cap = StrokeCap.Round,
            )
        }
        if (active) drawCircle(Theme.accent.copy(alpha = 0.15f), s / 2, center)
    }
}
