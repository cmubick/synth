//
//  Controls.kt
//
//  Faders, waveform switches, two-way switches, lit buttons and the labeled
//  modules of the front panel. Ported from Controls.swift.
//

package com.tuesdaygames.synth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuesdaygames.synth.engine.ParamInfo
import com.tuesdaygames.synth.model.SynthPatch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** A vertical slider, like the envelope faders on a hardware synth. */
@Composable
fun Fader(patch: SynthPatch, id: Int, label: String) {
    val info = ParamInfo.of(id)
    val value = patch[id]
    val height = LocalFaderHeight.current
    var dragging by remember { mutableStateOf(false) }
    val currentValue by rememberUpdatedState(value)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.semantics {
            contentDescription = info.name
            stateDescription = info.format(value)
        },
    ) {
        Canvas(
            Modifier
                .width(30.dp)
                .height(height)
                .pointerInput(id) {
                    var lastTapUp = 0L
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = currentValue
                        var moved = false
                        var totalDy = 0f
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (!moved) {
                                    if (change.uptimeMillis - lastTapUp < 300) {
                                        patch[id] = info.default
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
                                val travel = maxOf(this.size.height.toFloat(), 60.dp.toPx())
                                patch[id] = start - totalDy / travel * (info.max - info.min)
                            }
                        }
                        dragging = false
                    }
                },
        ) {
            val norm = info.normalized(value).coerceIn(0f, 1f)
            val trackW = 6.dp.toPx()
            val cap = 12.dp.toPx()
            val x = size.width / 2
            drawRoundRect(Theme.track, Offset(x - trackW / 2, 0f), Size(trackW, size.height), CornerRadius(trackW / 2))
            val fill = maxOf(trackW, norm * size.height)
            drawRoundRect(
                Theme.accent.copy(alpha = 0.85f),
                Offset(x - trackW / 2, size.height - fill),
                Size(trackW, fill),
                CornerRadius(trackW / 2),
            )
            val capY = size.height - cap - norm * (size.height - cap)
            val capW = 26.dp.toPx()
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFD9D9D9), Color(0xFF8C8C8C)), startY = capY, endY = capY + cap),
                Offset(x - capW / 2, capY),
                Size(capW, cap),
                CornerRadius(3.dp.toPx()),
            )
            drawRect(Color.Black.copy(alpha = 0.6f), Offset(x - capW / 2, capY + cap / 2 - 0.75.dp.toPx()), Size(capW, 1.5.dp.toPx()))
        }
        Text(
            text = if (dragging) info.format(value) else label,
            style = Theme.labelStyle(),
            color = if (dragging) Theme.accent else Theme.label,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(30.dp),
        )
    }
}

enum class WaveKind { Saw, Square, Triangle, Sine, Random }

/** Row of lit buttons for picking a waveform. */
@Composable
fun WaveSelector(
    patch: SynthPatch,
    id: Int,
    title: String,
    shapes: List<WaveKind> = listOf(WaveKind.Saw, WaveKind.Square, WaveKind.Triangle, WaveKind.Sine),
) {
    val selected = patch[id].roundToInt()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = Theme.labelStyle(), color = Theme.label)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            shapes.forEachIndexed { i, kind ->
                val isOn = i == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .background(if (isOn) Theme.accent else Theme.button, RoundedCornerShape(5.dp))
                        .clickable(role = Role.RadioButton) { patch[id] = i.toFloat() }
                        .semantics {
                            contentDescription = ParamInfo.of(id).format(i.toFloat())
                            this.selected = isOn
                        }
                        .padding(horizontal = 5.dp, vertical = 7.dp),
                ) {
                    WaveIcon(kind, if (isOn) Color.Black else Theme.cream, Modifier.size(18.dp, 11.dp))
                }
            }
        }
    }
}

/** Tiny drawings of each waveform for the selector buttons. */
@Composable
fun WaveIcon(kind: WaveKind, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path()
        when (kind) {
            WaveKind.Saw -> {
                p.moveTo(0f, h); p.lineTo(w / 2, 0f); p.lineTo(w / 2, h); p.lineTo(w, 0f)
            }
            WaveKind.Square -> {
                p.moveTo(0f, h); p.lineTo(0f, 0f); p.lineTo(w / 2, 0f); p.lineTo(w / 2, h); p.lineTo(w, h); p.lineTo(w, 0f)
            }
            WaveKind.Triangle -> {
                p.moveTo(0f, h); p.lineTo(w / 4, 0f); p.lineTo(3 * w / 4, h); p.lineTo(w, 0f)
            }
            WaveKind.Sine -> {
                p.moveTo(0f, h / 2)
                for (i in 1..24) {
                    val t = i / 24f
                    p.lineTo(t * w, h / 2 - sin(t * 2 * PI).toFloat() * h / 2)
                }
            }
            WaveKind.Random -> {
                val levels = listOf(0.7f, 0.1f, 0.9f, 0.4f)
                p.moveTo(0f, levels[0] * h)
                levels.forEachIndexed { i, level ->
                    p.lineTo(i * w / 4, level * h)
                    p.lineTo((i + 1) * w / 4, level * h)
                }
            }
        }
        drawPath(p, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Two-position switch, e.g. POLY / MONO. */
@Composable
fun SegmentSwitch(selected: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .background(Theme.button, RoundedCornerShape(7.dp))
            .padding(2.dp),
    ) {
        labels.forEachIndexed { i, text ->
            val isOn = i == selected
            Text(
                text,
                style = Theme.titleStyle(11.sp),
                color = if (isOn) Color.Black else Theme.label,
                modifier = Modifier
                    .background(if (isOn) Theme.accent else Color.Transparent, RoundedCornerShape(5.dp))
                    .clickable(role = Role.RadioButton) { onSelect(i) }
                    .semantics { this.selected = isOn }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

/** A button that lights up when on (ACCENT, REC, SONG). */
@Composable
fun LitButton(
    text: String,
    isOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Theme.accent,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 30.dp)
            .background(if (isOn) color else Theme.button, RoundedCornerShape(6.dp))
            .clickable(role = Role.Switch, onClick = onClick)
            .semantics { selected = isOn }
            .padding(horizontal = 6.dp),
    ) {
        Text(text, style = Theme.titleStyle(9.sp), color = if (isOn) Color.Black else Theme.label, maxLines = 1)
    }
}

/** A labeled section of the front panel. */
@Composable
fun Module(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxHeight()
            .background(Theme.module, RoundedCornerShape(10.dp))
            .border(1.dp, Theme.moduleEdge, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(title, style = Theme.titleStyle(10.sp), color = Theme.cream.copy(alpha = 0.6f), letterSpacing = 1.6.sp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            modifier = Modifier.weight(1f),
            content = content,
        )
    }
}

/** A small padlock, drawn so no icon library is needed. */
@Composable
fun LockIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val body = h * 0.55f
        drawRoundRect(color, Offset(0f, h - body), Size(w, body), CornerRadius(w * 0.15f))
        val stroke = w * 0.18f
        drawArc(
            color, 180f, 180f, false,
            Offset(w * 0.2f, stroke / 2), Size(w * 0.6f, (h - body) * 1.6f),
            style = Stroke(stroke),
        )
    }
}
