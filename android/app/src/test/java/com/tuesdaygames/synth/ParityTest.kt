//
//  ParityTest.kt
//
//  Android must sell, lock and ship the same things as iOS. These tests read
//  the iPhone app's Swift files straight from the repository and compare.
//  (The sound engine itself is the same C++ code on both, so it needs no
//  comparing; its own tests are Engine/tests.)
//

package com.tuesdaygames.synth

import com.tuesdaygames.synth.model.Beat
import com.tuesdaygames.synth.store.FullVersion
import com.tuesdaygames.synth.store.Store
import com.tuesdaygames.synth.ui.PixelFont
import com.tuesdaygames.synth.ui.SynthPalette
import com.tuesdaygames.synth.ui.TipJarArt
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParityTest {
    /** The repository root: tests run in android/app. */
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "iOS").isDirectory }
    private fun swift(path: String) = File(root, "iOS/Synth/$path").readText()

    @Test
    fun theSameProductIDs() {
        val store = swift("UI/Store.swift")
        assertTrue("static let fullGameID = \"${Store.fullGameID}\"" in store)
        val tips = Store.tipIDs.joinToString(", ") { "\"$it\"" }
        assertTrue("static let tipIDs = [$tips]" in store)
        val onSale = Regex("static let fullGameOnSale = (true|false)").find(store)!!.groupValues[1]
        assertEquals(onSale.toBoolean(), Store.fullGameOnSale)
    }

    @Test
    fun theSameFullVersionFeatures() {
        val store = swift("UI/Store.swift")
        val ios = Regex("""Feature\(title: "([^"]+)", detail: "([^"]+)", available: (true|false)\)""").findAll(store)
            .map { FullVersion.Feature(it.groupValues[1], it.groupValues[2], it.groupValues[3].toBoolean()) }.toList()
        assertEquals(ios, FullVersion.features)
        assertTrue("static let freePatternCount = ${FullVersion.FREE_PATTERN_COUNT}" in store)
    }

    @Test
    fun theSamePremadeBeatsSongsAndFreeOnes() {
        val beats = swift("Model/Beat.swift")
        val names = Regex("""name: "([^"]+)"""").findAll(beats).map { it.groupValues[1] }.toList()
        assertEquals(names, Beat.factory.map { it.name })
        val songs = Regex("""song: \[([0-9, ]*)\]""").findAll(beats).map { m ->
            m.groupValues[1].split(",").mapNotNull { it.trim().toIntOrNull() }
        }.toList()
        assertEquals(songs, Beat.factory.map { it.song })
        assertEquals(listOf("Boom Bap", "Four on the Floor", "Blank"), Beat.factory.filter { it.isFree }.map { it.name })
    }

    @Test
    fun theSamePixelFontAndColors() {
        val art = swift("UI/PixelArt.swift")
        val glyphs = Regex("""^\s+"(.)": \[""", RegexOption.MULTILINE).findAll(art.substringAfter("private static let glyphs")).count()
        // Every iOS glyph has an Android twin.
        assertTrue(glyphs > 40)
        assertEquals(PixelFont.width("808"), 17)
        assertTrue("static let accent = PixelColor(0x${"%06X".format(SynthPalette.accent.rgb)})" in art)
        // The tip jar is 14 x 13 and fills from the bottom.
        val empty = TipJarArt.jar(0)
        val full = TipJarArt.jar(3)
        assertEquals(14, empty.width)
        assertTrue(empty[5, 5] == null && full[5, 5] != null)
    }

    @Test
    fun feedbackGoesToTheSameAddress() {
        val feedback = swift("UI/Feedback.swift")
        assertTrue("static let address = \"${Feedback.ADDRESS}\"" in feedback)
        assertTrue("static let subject = \"${Feedback.SUBJECT}\"" in feedback)
        val body = Feedback.body("0.1.0 (1)", "Android 16", "Google Pixel 9", fullVersion = true)
        assertTrue(body.startsWith("\n\n"))
        assertTrue("808Synth 0.1.0 (1)" in body && "Android 16 · Google Pixel 9" in body && "Full version: yes" in body)
    }

    @Test
    fun locksMatchTheRules() {
        assertTrue(FullVersion.isLocked(2, owned = false))
        assertTrue(!FullVersion.isLocked(1, owned = false))
        assertTrue(!FullVersion.isLocked(7, owned = true))
        assertTrue(FullVersion.isLocked(Beat.electro, owned = false))
        assertTrue(!FullVersion.isLocked(Beat.boomBap, owned = false))
    }
}
