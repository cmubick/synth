//
//  App.kt
//
//  The whole screen: walnut side panels in the edges (where the camera cutout
//  is), the top bar, and either the synth panel and keyboard or the drum
//  machine. Ported from ContentView.swift.
//

package com.tuesdaygames.synth.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.tuesdaygames.synth.KeyValueStore
import com.tuesdaygames.synth.Prefs
import com.tuesdaygames.synth.model.DrumMachine
import com.tuesdaygames.synth.model.SynthPatch
import com.tuesdaygames.synth.store.FullVersion
import com.tuesdaygames.synth.store.StoreModel

@Composable
fun App(
    patch: SynthPatch,
    drums: DrumMachine,
    store: StoreModel,
    prefs: KeyValueStore,
    activity: Activity,
    keepScreenOn: (Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.Synth) }
    var octave by rememberSaveable { mutableIntStateOf(3) } // lowest key is C3
    var showMore by remember { mutableStateOf(false) }
    var settingsVersion by remember { mutableIntStateOf(0) }
    val owned = store.isUnlocked

    // Follow the engine every frame: playhead, pad flashes, recorded steps.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            drums.poll()
        }
    }

    // Don't let the phone dim and lock in the middle of a beat.
    LaunchedEffect(drums.isPlaying, settingsVersion) {
        keepScreenOn(drums.isPlaying && prefs.getBoolean(Prefs.KEEP_AWAKE, true))
    }

    // A refunded purchase turns the full-version features back off.
    LaunchedEffect(owned) {
        if (!owned) {
            if (drums.songMode) drums.changeSongMode(false)
            if (FullVersion.isLocked(drums.editPattern, owned)) drums.selectPattern(0)
        }
    }

    SynthTheme {
        CompositionLocalProvider(
            LocalOwned provides owned,
            LocalOpenMore provides { _: Boolean -> showMore = true },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().background(Theme.panel)) {
                val density = LocalDensity.current
                val direction = LocalLayoutDirection.current
                val insets = WindowInsets.displayCutout.union(WindowInsets.systemBars)
                val leftInset = with(density) { insets.getLeft(this, direction).toDp() }
                val rightInset = with(density) { insets.getRight(this, direction).toDp() }
                val bottomInset = with(density) { insets.getBottom(this).toDp() }
                // Walnut side panels sit in the screen's unsafe edges, at least 18 dp wide.
                val woodLeft = maxOf(leftInset, 18.dp)
                val woodRight = maxOf(rightInset, 18.dp)

                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(woodLeft).fillMaxHeight().background(Theme.wood))
                    Box(Modifier.weight(1f).fillMaxHeight())
                    Box(Modifier.width(woodRight).fillMaxHeight().background(Theme.wood))
                }

                val contentHeight = maxHeight - bottomInset - 12.dp
                val panelHeight = maxOf(150.dp, contentHeight * 0.47f)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = woodLeft + 8.dp, end = woodRight + 8.dp, top = 6.dp, bottom = bottomInset + 6.dp),
                ) {
                    TopBar(tab, { tab = it }, patch, drums)
                    when (tab) {
                        AppTab.Synth -> {
                            CompositionLocalProvider(
                                // Two rows of knobs plus labels and the module title fit the panel.
                                LocalKnobSize provides ((panelHeight - 60.dp) / 2 - 22.dp).coerceIn(34.dp, 64.dp),
                                LocalFaderHeight provides panelHeight - 58.dp,
                            ) {
                                SynthPanel(patch, Modifier.height(panelHeight))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                OctaveControl(octave) { octave = it }
                                Keyboard(
                                    lowestNote = (octave + 1) * 12,
                                    onNoteOn = { note, velocity -> patch.engine.noteOn(note, velocity) },
                                    onNoteOff = { patch.engine.noteOff(it) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black),
                                )
                            }
                        }
                        AppTab.Drums -> Box(Modifier.weight(1f)) { DrumScreen(drums) }
                    }
                }
            }

            if (showMore) {
                MoreSheet(
                    store = store,
                    prefs = prefs,
                    activity = activity,
                    onSettingsChanged = { settingsVersion++ },
                    onDismiss = { showMore = false },
                )
            }
        }
    }
}

@Composable
private fun OctaveControl(octave: Int, onChange: (Int) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        modifier = Modifier
            .width(50.dp)
            .fillMaxHeight()
            .background(Theme.module, RoundedCornerShape(8.dp)),
    ) {
        OctaveButton("+", "Octave up", octave < 6) { onChange(octave + 1) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("OCT", style = Theme.titleStyle(9.sp), color = Theme.label)
            Text("C$octave", style = Theme.titleStyle(15.sp), color = Theme.accent)
        }
        OctaveButton("−", "Octave down", octave > 0) { onChange(octave - 1) }
    }
}

@Composable
private fun OctaveButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp, 34.dp)
            .background(Theme.button, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Text(symbol, style = Theme.titleStyle(16.sp), color = if (enabled) Theme.cream else Theme.label.copy(alpha = 0.3f))
    }
}
