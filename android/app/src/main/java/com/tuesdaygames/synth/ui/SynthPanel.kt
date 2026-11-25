//
//  SynthPanel.kt
//
//  The front panel: every sound control, grouped like a hardware synth.
//  Scrolls sideways on phones. Ported from SynthPanel.swift.
//

package com.tuesdaygames.synth.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tuesdaygames.synth.engine.Param
import com.tuesdaygames.synth.model.SynthPatch

@Composable
fun SynthPanel(patch: SynthPatch, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp),
    ) {
        Module("OSCILLATORS") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WaveSelector(patch, Param.osc1Wave, "Osc 1")
                WaveSelector(patch, Param.osc2Wave, "Osc 2")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Knob(patch, Param.osc2Semi)
                Knob(patch, Param.osc2Fine)
                Knob(patch, Param.glide)
            }
        }

        Module("MIXER") {
            Knob(patch, Param.oscMix, label = "Osc 1 ↔ 2")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Knob(patch, Param.sub)
                Knob(patch, Param.noise)
            }
        }

        Module("FILTER") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Knob(patch, Param.cutoff, scale = 1.15f)
                Knob(patch, Param.resonance, scale = 1.15f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Knob(patch, Param.envAmount)
                Knob(patch, Param.keytrack)
                Knob(patch, Param.drive)
            }
        }

        Module("FILTER ENVELOPE") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fader(patch, Param.fenvAttack, "A")
                Fader(patch, Param.fenvDecay, "D")
                Fader(patch, Param.fenvSustain, "S")
                Fader(patch, Param.fenvRelease, "R")
            }
        }

        Module("AMP ENVELOPE") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fader(patch, Param.aenvAttack, "A")
                Fader(patch, Param.aenvDecay, "D")
                Fader(patch, Param.aenvSustain, "S")
                Fader(patch, Param.aenvRelease, "R")
            }
        }

        Module("LFO") {
            WaveSelector(
                patch, Param.lfoWave, "Shape",
                shapes = listOf(WaveKind.Sine, WaveKind.Triangle, WaveKind.Square, WaveKind.Random),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Knob(patch, Param.lfoRate)
                Knob(patch, Param.lfoPitch)
                Knob(patch, Param.lfoFilter)
            }
        }

        Module("ECHO") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Knob(patch, Param.delayTime)
                Knob(patch, Param.delayFeedback)
            }
            Knob(patch, Param.delayMix, label = "Amount")
        }
    }
}
