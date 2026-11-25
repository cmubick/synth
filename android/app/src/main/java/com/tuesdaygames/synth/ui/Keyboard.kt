//
//  Keyboard.kt
//
//  The on-screen piano. Every finger is tracked on its own: play chords, and
//  slide a finger across keys to glide between notes. Touching lower on a key
//  plays it louder. Ported from KeyboardView.swift.
//

package com.tuesdaygames.synth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Where the keys are, for a keyboard `width` x `height` pixels starting at `lowestNote` (a C). */
class KeyLayout(val width: Float, val height: Float, val lowestNote: Int, minKeyWidth: Float) {
    val whiteCount = maxOf(7, (width / minKeyWidth).toInt())
    val whiteWidth = width / whiteCount
    val blackWidth = whiteWidth * 0.62f
    val blackHeight = height * 0.6f

    fun whiteNote(index: Int) = lowestNote + 12 * (index / 7) + whiteOffsets[index % 7]

    /** Black keys as (note, left edge), skipping one that would hang off the right edge. */
    val blackKeys: List<Pair<Int, Float>> = (0 until whiteCount - 1).mapNotNull { i ->
        if (i % 7 !in hasBlackAfter) null else whiteNote(i) + 1 to (i + 1) * whiteWidth - blackWidth / 2
    }

    /** The note and velocity under a finger, or null if it's off the keyboard. */
    fun hit(p: Offset): Pair<Int, Float>? {
        if (p.x < 0 || p.y < 0 || p.x > width || p.y > height) return null
        if (p.y < blackHeight) {
            blackKeys.firstOrNull { (_, x) -> p.x >= x && p.x <= x + blackWidth }?.let {
                return it.first to velocity(p.y, blackHeight)
            }
        }
        val index = (p.x / whiteWidth).toInt().coerceIn(0, whiteCount - 1)
        return whiteNote(index) to velocity(p.y, height)
    }

    private fun velocity(y: Float, keyHeight: Float) = 0.4f + 0.6f * (y / keyHeight).coerceIn(0f, 1f)

    companion object {
        private val whiteOffsets = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        private val hasBlackAfter = setOf(0, 1, 3, 4, 5) // C D F G A
    }
}

@Composable
fun Keyboard(
    lowestNote: Int,
    onNoteOn: (Int, Float) -> Unit,
    onNoteOff: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // How many fingers are on each note (two fingers can share one).
    val held = remember { mutableStateMapOf<Int, Int>() }
    val noteOn by rememberUpdatedState(onNoteOn)
    val noteOff by rememberUpdatedState(onNoteOff)
    val measurer = rememberTextMeasurer()

    fun press(note: Int, velocity: Float) {
        held[note] = (held[note] ?: 0) + 1
        noteOn(note, velocity)
    }

    fun release(note: Int) {
        val count = held[note] ?: return
        if (count <= 1) {
            held.remove(note)
            noteOff(note)
        } else {
            held[note] = count - 1
        }
    }

    // Changing octave, or leaving the screen, lets go of everything.
    DisposableEffect(lowestNote) {
        onDispose {
            held.keys.toList().forEach(noteOff)
            held.clear()
        }
    }

    Canvas(
        modifier
            .semantics { contentDescription = "Keyboard" }
            .pointerInput(lowestNote) {
                val minKey = 44.dp.toPx()
                awaitPointerEventScope {
                    val fingers = mutableMapOf<PointerId, Int>()
                    while (true) {
                        val event = awaitPointerEvent()
                        val layout = KeyLayout(size.width.toFloat(), size.height.toFloat(), lowestNote, minKey)
                        for (change in event.changes) {
                            val old = fingers[change.id]
                            if (change.pressed) {
                                val key = layout.hit(change.position)
                                if (key?.first != old) {
                                    old?.let(::release)
                                    if (key != null) {
                                        fingers[change.id] = key.first
                                        press(key.first, key.second)
                                    } else {
                                        fingers.remove(change.id)
                                    }
                                }
                                change.consume()
                            } else if (old != null) {
                                fingers.remove(change.id)
                                release(old)
                            }
                        }
                    }
                }
            },
    ) {
        val layout = KeyLayout(size.width, size.height, lowestNote, 44.dp.toPx())
        drawKeys(layout, held.keys) { note, x, y ->
            val name = "C${note / 12 - 1}"
            val text = measurer.measure(name, TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.Black.copy(alpha = 0.45f)))
            drawText(text, topLeft = Offset(x - text.size.width / 2f, y - text.size.height))
        }
    }
}

private fun DrawScope.drawKeys(layout: KeyLayout, held: Set<Int>, label: DrawScope.(Int, Float, Float) -> Unit) {
    val accent = Theme.accent
    val ivory = Color(0xFFF5F0E3)
    val ivoryShade = Color(0xFFD6CFBF)
    val corner = CornerRadius(5.dp.toPx())

    for (i in 0 until layout.whiteCount) {
        val note = layout.whiteNote(i)
        val x = i * layout.whiteWidth + 1.dp.toPx()
        val w = layout.whiteWidth - 2.dp.toPx()
        val down = note in held
        drawRoundRect(if (down) accent else ivory, Offset(x, -corner.y), Size(w, size.height + corner.y), corner)
        if (!down) drawRect(ivoryShade.copy(alpha = 0.6f), Offset(x, size.height - 6.dp.toPx()), Size(w, 3.dp.toPx()))
        if (note % 12 == 0) label(note, x + w / 2, size.height - 10.dp.toPx())
    }
    for ((note, x) in layout.blackKeys) {
        val down = note in held
        val keyCorner = CornerRadius(4.dp.toPx())
        if (down) {
            drawRoundRect(accent, Offset(x, -keyCorner.y), Size(layout.blackWidth, layout.blackHeight + keyCorner.y), keyCorner)
        } else {
            drawRoundRect(Color(0xFF1A1A1A), Offset(x, -keyCorner.y), Size(layout.blackWidth, layout.blackHeight + keyCorner.y), keyCorner)
            drawRoundRect(
                Color(0xFF3D3D3D),
                Offset(x + 3.dp.toPx(), 0f),
                Size(layout.blackWidth - 6.dp.toPx(), layout.blackHeight - 6.dp.toPx()),
                CornerRadius(3.dp.toPx()),
            )
        }
    }
}
