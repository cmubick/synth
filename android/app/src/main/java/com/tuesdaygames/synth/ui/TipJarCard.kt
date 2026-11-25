//
//  TipJarCard.kt
//
//  The tip jar in the More screen, 8-bit like Skate's (its font, its boxes and
//  wording) in 808Synth's colors. The jar fills with coins as tips come in.
//  Ported from iOS's TipJarCard.swift.
//

package com.tuesdaygames.synth.ui

import android.app.Activity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tuesdaygames.synth.KeyValueStore
import com.tuesdaygames.synth.Prefs
import com.tuesdaygames.synth.haptics.Haptics
import com.tuesdaygames.synth.store.PurchaseOutcome
import com.tuesdaygames.synth.store.StoreModel

/** Skate's names and prices, shown before Google Play answers. */
private val tipLabels = listOf("SMALL" to "$0.99", "GENEROUS" to "$2.99", "BIG" to "$4.99")

@Composable
fun TipJarCard(store: StoreModel, prefs: KeyValueStore, activity: Activity) {
    var tipsGiven by remember { mutableIntStateOf(prefs.getInt(Prefs.TIPS_GIVEN)) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val state = store.state

    PixelPanel(SynthPalette.module, SynthPalette.edge, modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                PixelImage(TipJarArt.jar(tipsGiven), 5.dp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelText("TIP JAR", SynthPalette.accent, 3.dp)
                    PixelText("TIPS ARE OPTIONAL AND", SynthPalette.creamDark, 2.dp)
                    PixelText("DON'T UNLOCK ANYTHING.", SynthPalette.creamDark, 2.dp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                tipLabels.forEachIndexed { index, (name, fallbackPrice) ->
                    val product = state.tips.getOrNull(index)
                    val price = product?.price ?: fallbackPrice
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelButton(
                            label = price,
                            description = "${name.lowercase().replaceFirstChar { it.uppercase() }} tip, $price",
                            enabled = !busy,
                        ) {
                            if (product == null) {
                                message = if (state.hasLoaded) "TIPS AREN'T SET UP YET" else "ONE MOMENT..."
                                if (!state.hasLoaded) store.refresh()
                                return@PixelButton
                            }
                            busy = true
                            store.buy(product, activity) { outcome ->
                                busy = false
                                message = when (outcome) {
                                    PurchaseOutcome.Success -> {
                                        tipsGiven += 1
                                        prefs.putInt(Prefs.TIPS_GIVEN, tipsGiven)
                                        Haptics.thump()
                                        "THANK YOU!"
                                    }
                                    PurchaseOutcome.Pending -> "WAITING FOR APPROVAL"
                                    PurchaseOutcome.Cancelled -> ""
                                    PurchaseOutcome.Failed -> "THAT DIDN'T WORK. TRY AGAIN?"
                                }
                            }
                        }
                        PixelText(name, SynthPalette.grey, 2.dp)
                    }
                }
            }
            if (message.isNotEmpty()) PixelText(message, SynthPalette.stepYellow, 3.dp)
        }
    }
}

/** A chunky 8-bit button: pixel-font text on a pixel box that drops a pixel when pressed. */
@Composable
fun PixelButton(label: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val pixel = 3.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(y = if (pressed) pixel else 0.dp)
            .widthIn(min = 108.dp)
            .drawBehind {
                pixelBox(
                    (if (pressed) SynthPalette.accentDark else SynthPalette.accent).toColor(),
                    if (pressed) null else SynthPalette.stepYellow.toColor(),
                    pixel.toPx(),
                )
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { onClick(); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    val up = waitForUpOrCancellation()
                    pressed = false
                    if (up != null) onClick()
                }
            }
            .padding(horizontal = pixel * 4, vertical = pixel * 3),
    ) {
        PixelText(label, PixelColor(0xFCFCFC), pixel)
    }
}
