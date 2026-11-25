//
//  DrumScreen.kt
//
//  The DRUMS tab: pads on the left, sound and pattern controls on the right,
//  and the 16 step buttons along the bottom. Ported from DrumScreen.swift.
//

package com.tuesdaygames.synth.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuesdaygames.synth.engine.NativeEngine
import com.tuesdaygames.synth.haptics.Haptics
import com.tuesdaygames.synth.model.DrumMachine
import com.tuesdaygames.synth.store.FullVersion
import kotlin.math.roundToInt

/** Opens the More sheet; `true` jumps to the full version. Set by App. */
val LocalOpenMore = compositionLocalOf<(Boolean) -> Unit> { {} }

/** Whether the full version is owned. Set by App from the store. */
val LocalOwned = compositionLocalOf { false }

@Composable
fun DrumScreen(drums: DrumMachine) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val stepsHeight = (maxHeight * 0.27f).coerceIn(64.dp, 92.dp)
        val rightWidth = maxWidth * 0.6f - 8.dp
        // Six knobs (each knob + 16 dp of label room) plus the drum name and padding
        // must fit the right-hand column; they also scale with height.
        val byWidth = (rightWidth - 85.dp) / 6 - 16.dp
        val knob = minOf(42.dp, maxOf(28.dp, minOf(byWidth, (maxHeight - stepsHeight) * 0.16f)))
        val padsWidth = maxWidth * 0.4f

        CompositionLocalProvider(LocalKnobSize provides knob) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    PadGrid(drums, Modifier.width(padsWidth).fillMaxHeight())
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        TrackControls(drums)
                        PatternBar(drums)
                        SongStrip(drums, Modifier.weight(1f))
                    }
                }
                StepRow(drums, Modifier.height(stepsHeight))
            }
        }
    }
}

// MARK: - Pads

@Composable
private fun PadGrid(drums: DrumMachine, modifier: Modifier) {
    val columns = 5
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        for (row in 0 until DrumMachine.TRACKS / columns) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                for (col in 0 until columns) DrumPad(drums, row * columns + col)
            }
        }
    }
}

@Composable
private fun RowScope.DrumPad(drums: DrumMachine, track: Int) {
    val info = drums.tracks[track]
    val selected = drums.selectedTrack == track
    var pressed by remember { mutableStateOf(false) }
    val glow = remember { Animatable(0f) }
    val hits = drums.hits[track]
    var lastHits by remember { mutableStateOf(hits) }

    // Flash whenever this drum plays (from the sequencer or a finger).
    LaunchedEffect(hits) {
        if (hits != lastHits) {
            lastHits = hits
            glow.snapTo(1f)
            glow.animateTo(0f, tween(250))
        }
    }
    val lit = glow.value > 0.4f
    val shape = RoundedCornerShape(10.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .scale(if (pressed) 0.95f else 1f)
            .background(Brush.verticalGradient(listOf(Color(0xFF424242), Color(0xFF292929))), shape)
            .background(Theme.accent.copy(alpha = glow.value * 0.85f), shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Theme.accent else Color.White.copy(alpha = 0.08f), shape)
            .semantics(mergeDescendants = true) {
                contentDescription = info.name
                this.selected = selected
            }
            // Fires on touch-down (not release), and each pad follows its own finger.
            .pointerInput(track) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    Haptics.tap()
                    drums.hitPad(track)
                    waitForUpOrCancellation()
                    pressed = false
                }
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(info.shortName, style = Theme.titleStyle(20.sp), color = if (lit) Color.Black else Theme.cream)
            Text(
                info.name,
                style = Theme.labelStyle(9.sp),
                color = if (lit) Color.Black.copy(alpha = 0.7f) else Theme.label,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
        // Dot: this drum has steps in the pattern being edited.
        if (drums.hasSteps(track)) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(5.dp)
                    .background(Theme.accent, CircleShape),
            )
        }
    }
}

// MARK: - Sound controls for the selected drum

@Composable
private fun TrackControls(drums: DrumMachine) {
    val track = drums.selectedTrack
    val info = drums.tracks[track]
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.module, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Text(
            info.name.uppercase(),
            style = Theme.titleStyle(12.sp),
            color = Theme.accent,
            maxLines = 2,
            modifier = Modifier
                .width(52.dp)
                .padding(top = 4.dp),
        )
        for (p in 0 until DrumMachine.SOUND_PARAMS) {
            RotaryKnob(
                label = info.paramNames[p],
                value = drums.soundValue(track, p),
                range = 0f..1f,
                default = info.defaults[p],
                format = { NativeEngine.drumParamFormat(p, it) },
                onChange = { drums.setSound(track, p, it) },
                bipolar = p == DrumMachine.TUNE,
            )
        }
        Box(
            Modifier
                .padding(vertical = 4.dp)
                .width(1.dp)
                .height(LocalKnobSize.current + 16.dp)
                .background(Theme.moduleEdge),
        )
        RotaryKnob(
            label = "Swing", value = drums.swing, range = 0f..1f, default = 0f,
            format = { "${(50 + 25 * it).roundToInt()}%" },
            onChange = drums::changeSwing,
        )
        RotaryKnob(
            label = "Kit Vol", value = drums.volume, range = 0f..1f, default = 0.8f,
            format = { "${(it * 100).roundToInt()}%" },
            onChange = drums::changeVolume,
        )
    }
}

// MARK: - Patterns A–H

@Composable
private fun PatternBar(drums: DrumMachine) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (p in 0 until DrumMachine.PATTERNS) PatternButton(drums, p)
        SongButton(drums)
        PatternMenu(drums)
    }
}

@Composable
private fun RowScope.PatternButton(drums: DrumMachine, pattern: Int) {
    val owned = LocalOwned.current
    val openMore = LocalOpenMore.current
    val editing = drums.editPattern == pattern
    val playing = drums.isPlaying && drums.playingPattern == pattern
    val locked = FullVersion.isLocked(pattern, owned)
    val hasSteps = drums.patternHasSteps(pattern)
    val name = DrumMachine.patternNames[pattern]

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 34.dp)
            .background(if (editing) Theme.accent else Theme.button, RoundedCornerShape(6.dp))
            .clickable(role = Role.Button) {
                Haptics.tick()
                if (locked) openMore(true) else drums.selectPattern(pattern)
            }
            .semantics {
                contentDescription = "Pattern $name" + if (locked) ", locked" else ""
                selected = editing
            },
    ) {
        Text(
            name,
            style = Theme.titleStyle(14.sp),
            color = when {
                editing -> Color.Black
                hasSteps && !locked -> Theme.cream
                else -> Theme.label.copy(alpha = 0.4f)
            },
        )
        Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            if (locked) {
                LockIcon(Theme.label.copy(alpha = 0.6f), Modifier.size(7.dp, 8.dp))
            } else if (playing) {
                Box(Modifier.size(6.dp).background(Theme.green, CircleShape))
            }
        }
    }
}

/** SONG on/off. Song mode is part of the full version; until it's owned the
 *  button shows a lock and opens the unlock screen instead. */
@Composable
private fun SongButton(drums: DrumMachine) {
    val owned = LocalOwned.current
    val openMore = LocalOpenMore.current
    if (owned) {
        LitButton("SONG", drums.songMode, { drums.changeSongMode(!drums.songMode) })
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .heightIn(min = 30.dp)
                .background(Theme.button, RoundedCornerShape(6.dp))
                .clickable(role = Role.Button) { openMore(true) }
                .semantics(mergeDescendants = true) { contentDescription = "Song mode, locked" }
                .padding(horizontal = 6.dp),
        ) {
            Text("SONG", style = Theme.titleStyle(11.sp), color = Theme.label)
            LockIcon(Theme.label, Modifier.size(9.dp, 10.dp))
        }
    }
}

@Composable
private fun PatternMenu(drums: DrumMachine) {
    val owned = LocalOwned.current
    var open by remember { mutableStateOf(false) }
    val name = DrumMachine.patternNames[drums.editPattern]
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .background(Theme.button, RoundedCornerShape(6.dp))
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = "Pattern options" },
        ) {
            Text("•••", style = Theme.titleStyle(12.sp), color = Theme.cream)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Clear ${drums.tracks[drums.selectedTrack].name} in $name") },
                onClick = { drums.clearTrack(); open = false },
            )
            DropdownMenuItem(
                text = { Text("Clear Pattern $name", color = Color(0xFFFF6B5E)) },
                onClick = { drums.clearPattern(); open = false },
            )
            HorizontalDivider()
            for (p in 0 until DrumMachine.PATTERNS) {
                if (p != drums.editPattern && !FullVersion.isLocked(p, owned)) {
                    DropdownMenuItem(
                        text = { Text("Copy $name to Pattern ${DrumMachine.patternNames[p]}") },
                        onClick = { drums.copyPattern(p); open = false },
                    )
                }
            }
            HorizontalDivider()
            for (length in listOf(16, 12, 8, 6, 4, 3)) {
                val current = drums.lengths[drums.editPattern] == length
                DropdownMenuItem(
                    text = { Text("$length steps" + if (current) "  ✓" else "") },
                    onClick = { drums.setLength(length); open = false },
                )
            }
        }
    }
}

/** The song: the order patterns play in when SONG is on. */
@Composable
private fun SongStrip(drums: DrumMachine, modifier: Modifier) {
    val owned = LocalOwned.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
    ) {
        Text(
            "SONG",
            style = Theme.titleStyle(9.sp),
            color = if (drums.songMode) Theme.accent else Theme.label.copy(alpha = 0.5f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
        ) {
            if (drums.song.isEmpty()) {
                Text(
                    when {
                        drums.songMode -> "Tap patterns above to add them in order"
                        owned -> "Turn on SONG to chain patterns"
                        else -> "Chain patterns into songs with the full version"
                    },
                    style = Theme.labelStyle(10.sp),
                    color = Theme.label.copy(alpha = 0.6f),
                )
            }
            drums.song.forEachIndexed { i, p ->
                val current = drums.songMode && drums.isPlaying && drums.songPosition == i
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp, 22.dp)
                        .background(if (current) Theme.accent else Theme.button, RoundedCornerShape(4.dp)),
                ) {
                    Text(
                        DrumMachine.patternNames[p],
                        style = Theme.titleStyle(11.sp),
                        color = if (current) Color.Black else Theme.cream.copy(alpha = if (drums.songMode) 1f else 0.4f),
                    )
                }
            }
        }
        if (drums.songMode && drums.song.isNotEmpty()) {
            Text(
                "⌫",
                color = Theme.cream,
                modifier = Modifier
                    .clickable(role = Role.Button) { drums.removeLastFromSong() }
                    .semantics { contentDescription = "Remove last pattern from song" }
                    .padding(4.dp),
            )
            Text(
                "Clear",
                style = Theme.labelStyle(11.sp),
                color = Theme.cream,
                modifier = Modifier
                    .clickable(role = Role.Button) { drums.clearSong() }
                    .padding(4.dp),
            )
        }
    }
}

// MARK: - The 16 steps

@Composable
private fun StepRow(drums: DrumMachine, modifier: Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            modifier = Modifier
                .width(62.dp)
                .fillMaxHeight(),
        ) {
            LitButton("ACCENT", drums.accentMode, { drums.accentMode = !drums.accentMode }, Modifier.fillMaxWidth())
            LitButton("● REC", drums.recordMode, { drums.recordMode = !drums.recordMode }, Modifier.fillMaxWidth(), color = Color(0xFFFF3B30))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            for (s in 0 until DrumMachine.STEPS) StepButton(drums, s)
        }
    }
}

@Composable
private fun RowScope.StepButton(drums: DrumMachine, step: Int) {
    val value = drums.step(drums.selectedTrack, step)
    val color = Theme.beatColors[step / 4]
    val inPattern = step < drums.lengths[drums.editPattern]
    val playhead = drums.isPlaying && drums.currentStep == step && drums.playingPattern == drums.editPattern

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .alpha(if (inPattern) 1f else 0.25f)
            .clickable(enabled = inPattern, role = Role.Button) {
                Haptics.tick()
                drums.toggleStep(step)
            }
            .semantics {
                contentDescription = "Step ${step + 1}"
                stateDescription = when (value) { 0 -> "off"; 2 -> "accent"; else -> "on" }
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(if (playhead) 2.dp else 0.dp, if (playhead) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                .padding(if (playhead) 2.dp else 0.dp)
                .background(if (value > 0) color else Color(0xFF333333), RoundedCornerShape(5.dp)),
        ) {
            if (value == 2) Text("^", style = Theme.titleStyle(13.sp), color = Color.Black.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(4.dp)
                .background(color.copy(alpha = if (value > 0) 0f else 0.8f)),
        )
        Text("${step + 1}", style = Theme.labelStyle(9.sp), color = Theme.label.copy(alpha = 0.6f), modifier = Modifier.padding(top = 2.dp))
    }
}
