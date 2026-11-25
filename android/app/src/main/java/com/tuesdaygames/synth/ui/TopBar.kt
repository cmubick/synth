//
//  TopBar.kt
//
//  The top bar on both tabs: SYNTH / DRUMS, the sound or beat picker, POLY /
//  MONO, play/stop and tempo (so the beat keeps going while you play the
//  synth), volume and More. Ported from ContentView.swift and TransportView.swift.
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuesdaygames.synth.engine.Param
import com.tuesdaygames.synth.haptics.Haptics
import com.tuesdaygames.synth.model.Beat
import com.tuesdaygames.synth.model.DrumMachine
import com.tuesdaygames.synth.model.Preset
import com.tuesdaygames.synth.model.SynthPatch
import com.tuesdaygames.synth.store.FullVersion
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AppTab(val title: String) { Synth("SYNTH"), Drums("DRUMS") }

@Composable
fun TopBar(tab: AppTab, onTab: (AppTab) -> Unit, patch: SynthPatch, drums: DrumMachine) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(40.dp),
    ) {
        // Labels that must never be squeezed keep their size; the sound/beat name
        // and the volume slider give way on small phones.
        TabSwitch(tab, onTab)
        when (tab) {
            AppTab.Synth -> PresetPicker(patch, Modifier.weight(1f, fill = false))
            AppTab.Drums -> BeatPicker(drums, Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.weight(0.01f))
        if (tab == AppTab.Synth) {
            SegmentSwitch(patch[Param.voiceMode].roundToInt(), listOf("POLY", "MONO")) { patch[Param.voiceMode] = it.toFloat() }
        }
        Transport(drums)
        VolumeSlider(patch[Param.volume], { patch[Param.volume] = it }, Modifier.widthIn(min = 56.dp, max = 80.dp))
        MoreButton()
    }
}

@Composable
private fun TabSwitch(tab: AppTab, onTab: (AppTab) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .background(Theme.button, RoundedCornerShape(7.dp))
            .padding(2.dp),
    ) {
        AppTab.entries.forEach { t ->
            val isOn = t == tab
            Text(
                t.title,
                style = Theme.titleStyle(11.sp),
                letterSpacing = 1.sp,
                color = if (isOn) Color.Black else Theme.label,
                modifier = Modifier
                    .background(if (isOn) Theme.cream else Color.Transparent, RoundedCornerShape(5.dp))
                    .clickable(role = Role.Tab) { onTab(t) }
                    .semantics { selected = isOn }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PresetPicker(patch: SynthPatch, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(7.dp))
            .border(1.dp, Theme.moduleEdge, RoundedCornerShape(7.dp)),
    ) {
        Arrow("‹", "Previous sound") { patch.load(patch.presetIndex - 1) }
        Box {
            Text(
                patch.presetName,
                style = Theme.titleStyle(13.sp),
                color = Theme.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(min = 64.dp, max = 120.dp)
                    .clickable(role = Role.DropdownList) { open = true }
                    .padding(vertical = 8.dp),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Preset.factory.forEachIndexed { i, preset ->
                    DropdownMenuItem(
                        text = { Text(preset.name + if (i == patch.presetIndex) "  ✓" else "") },
                        onClick = { patch.load(i); open = false },
                    )
                }
            }
        }
        Arrow("›", "Next sound") { patch.load(patch.presetIndex + 1) }
    }
}

@Composable
private fun Arrow(symbol: String, description: String, onClick: () -> Unit) {
    Text(
        symbol,
        style = Theme.titleStyle(18.sp),
        color = Theme.label,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 9.dp, vertical = 2.dp),
    )
}

@Composable
private fun BeatPicker(drums: DrumMachine, modifier: Modifier) {
    val owned = LocalOwned.current
    val openMore = LocalOpenMore.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .widthIn(min = 96.dp, max = 190.dp)
                .height(34.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(7.dp))
                .border(1.dp, Theme.moduleEdge, RoundedCornerShape(7.dp))
                .clickable(role = Role.DropdownList) { open = true }
                .semantics(mergeDescendants = true) { contentDescription = "Beat: ${drums.beatName}" }
                .padding(horizontal = 10.dp),
        ) {
            Text("♫", color = Theme.accent, fontSize = 12.sp)
            Text(drums.beatName, style = Theme.titleStyle(13.sp), color = Theme.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("▾", color = Theme.accent, fontSize = 11.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text("Premade beats", style = Theme.labelStyle(11.sp), color = Theme.label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            Beat.factory.forEach { beat ->
                val locked = FullVersion.isLocked(beat, owned)
                DropdownMenuItem(
                    // Locked beats stay in the list, so players can see what the full version adds.
                    leadingIcon = if (locked) ({ LockIcon(Theme.cream, Modifier.size(11.dp, 13.dp)) }) else null,
                    text = { Text(beat.name) },
                    onClick = {
                        open = false
                        if (locked) openMore(true) else drums.load(beat)
                    },
                )
            }
        }
    }
}

/** Play/stop, tempo and tap tempo. */
@Composable
private fun Transport(drums: DrumMachine) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp, 34.dp)
                .background(if (drums.isPlaying) Theme.green else Theme.button, RoundedCornerShape(7.dp))
                .clickable(role = Role.Button) {
                    Haptics.thump()
                    drums.togglePlay()
                }
                .semantics { contentDescription = if (drums.isPlaying) "Stop" else "Play" },
        ) {
            Canvas(Modifier.size(13.dp)) {
                if (drums.isPlaying) {
                    drawRect(Color.Black, Offset(size.width * 0.08f, size.height * 0.08f), size * 0.84f)
                } else {
                    val p = Path().apply {
                        moveTo(size.width * 0.15f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * 0.15f, size.height); close()
                    }
                    drawPath(p, Theme.cream)
                }
            }
        }
        TempoDisplay(drums)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .background(Theme.button, RoundedCornerShape(7.dp))
                .clickable(role = Role.Button) { drums.tapTempo() }
                .semantics { contentDescription = "Tap tempo" },
        ) {
            Text("TAP", style = Theme.titleStyle(10.sp), color = Theme.label)
        }
    }
}

/** Tempo readout: drag up/down to change it, double-tap for 120. */
@Composable
private fun TempoDisplay(drums: DrumMachine) {
    var dragging by remember { mutableStateOf(false) }
    val tempo by rememberUpdatedState(drums.tempo)
    // A beat light that blinks on every quarter note.
    val onBeat = drums.isPlaying && drums.currentStep >= 0 && drums.currentStep % 4 == 0

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .size(58.dp, 34.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(7.dp))
            .semantics {
                contentDescription = "Tempo"
                stateDescription = "${drums.tempo.roundToInt()} BPM"
            }
            .pointerInput(Unit) {
                var lastTapUp = 0L
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = tempo
                    var moved = false
                    var totalDy = 0f
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!moved) {
                                if (change.uptimeMillis - lastTapUp < 300) {
                                    drums.changeTempo(120f)
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
                            drums.changeTempo(start - totalDy / 3.dp.toPx())
                        }
                    }
                    dragging = false
                }
            },
    ) {
        Box(Modifier.size(6.dp).background(if (onBeat) Theme.accent else Theme.track, CircleShape))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${drums.tempo.roundToInt()}",
                style = Theme.titleStyle(17.sp),
                color = if (dragging) Theme.accent else Theme.cream,
            )
            Text("BPM", style = Theme.titleStyle(8.sp), color = Theme.label, modifier = Modifier.padding(top = 0.dp))
        }
    }
}

@Composable
private fun MoreButton() {
    val openMore = LocalOpenMore.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .background(Theme.button, RoundedCornerShape(7.dp))
            .clickable(role = Role.Button) { openMore(false) }
            .semantics { contentDescription = "More: tip jar, full version and settings" },
    ) {
        Text("☰", style = Theme.titleStyle(15.sp), color = Theme.cream)
    }
}

/** The volume slider: an orange fill on a dark track with a white thumb, like iOS's. */
@Composable
private fun VolumeSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    val current by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .height(34.dp)
            .fillMaxWidth()
            .semantics {
                contentDescription = "Volume"
                stateDescription = "${(value * 100).roundToInt()}%"
            }
            .pointerInput(Unit) {
                val thumb = 10.dp.toPx()
                fun valueAt(x: Float) = ((x - thumb) / (size.width - 2 * thumb)).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    current(valueAt(down.position.x))
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        current(valueAt(change.position.x))
                    }
                }
            },
    ) {
        val thumb = 10.dp.toPx()
        val track = 4.dp.toPx()
        val y = size.height / 2
        val x = thumb + value.coerceIn(0f, 1f) * (size.width - 2 * thumb)
        drawLine(Theme.track, Offset(thumb, y), Offset(size.width - thumb, y), track, StrokeCap.Round)
        drawLine(Theme.accent, Offset(thumb, y), Offset(x, y), track, StrokeCap.Round)
        drawCircle(Color.Black.copy(alpha = 0.3f), thumb, Offset(x, y + 1.dp.toPx()))
        drawCircle(Color.White, thumb, Offset(x, y))
    }
}
