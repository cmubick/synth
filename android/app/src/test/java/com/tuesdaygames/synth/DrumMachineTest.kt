//
//  DrumMachineTest.kt
//
//  The drum machine model against a pretend engine: what the screen does is
//  written through to the engine, and premade beats load as on iOS.
//

package com.tuesdaygames.synth

import com.tuesdaygames.synth.engine.SoundEngine
import com.tuesdaygames.synth.model.Beat
import com.tuesdaygames.synth.model.DrumMachine
import com.tuesdaygames.synth.model.TrackInfo
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Records what the model asks of the engine. */
class FakeEngine : SoundEngine {
    val steps = mutableMapOf<Triple<Int, Int, Int>, Int>()
    var lastChain = intArrayOf()
    var lastTempo = 0f
    var playing = false
    val triggers = mutableListOf<Pair<Int, Float>>()
    val recorded = mutableListOf<Pair<Int, Boolean>>()
    var pollAnswer = IntArray(14)

    override fun noteOn(note: Int, velocity: Float) {}
    override fun noteOff(note: Int) {}
    override fun allNotesOff() {}
    override fun setParam(id: Int, value: Float) {}
    override fun drumTrigger(track: Int, velocity: Float) { triggers += track to velocity }
    override fun drumSetParam(track: Int, param: Int, value: Float) {}
    override fun drumSetVolume(volume: Float) {}
    override fun setStep(pattern: Int, track: Int, step: Int, value: Int) { steps[Triple(pattern, track, step)] = value }
    override fun getStep(pattern: Int, track: Int, step: Int) = steps[Triple(pattern, track, step)] ?: 0
    override fun setLength(pattern: Int, steps: Int) {}
    override fun setTempo(bpm: Float) { lastTempo = bpm }
    override fun setSwing(amount: Float) {}
    override fun setChain(patterns: IntArray) { lastChain = patterns }
    override fun play() { playing = true }
    override fun stop() { playing = false }
    override fun recordHit(track: Int, accent: Boolean) { recorded += track to accent }
    override fun poll(out: IntArray) { pollAnswer.copyInto(out) }
}

val testKit = listOf("BD", "SD", "CP", "LT", "HT", "RS", "CB", "CH", "OH", "CY").map {
    TrackInfo(it, it, listOf("Level", "Tune", "Decay", "Tone"), listOf(0.8f, 0.5f, 0.5f, 0.5f))
}

class DrumMachineTest {
    private fun make(clock: () -> Double = { 0.0 }) = FakeEngine().let { it to DrumMachine(it, testKit, clock) }

    @Test
    fun startsWithTheFirstPremadeBeatLoaded() {
        val (engine, drums) = make()
        assertEquals("Boom Bap", drums.beatName)
        assertEquals(90f, drums.tempo)
        assertEquals(90f, engine.lastTempo)
        // Boom Bap's kick: X... ...x ..x. .... → accent on 1, hits on 8 and 11.
        assertEquals(2, drums.step(0, 0))
        assertEquals(1, drums.step(0, 7))
        assertEquals(1, drums.step(0, 10))
        assertEquals(2, engine.getStep(0, 0, 0))
        assertContentEquals(intArrayOf(0), engine.lastChain)
    }

    @Test
    fun tappingAStepTurnsItOnAndOffAndAccentModeAccents() {
        val (engine, drums) = make()
        drums.selectedTrack = 3
        drums.toggleStep(5)
        assertEquals(1, engine.getStep(0, 3, 5))
        drums.toggleStep(5)
        assertEquals(0, engine.getStep(0, 3, 5))
        drums.accentMode = true
        drums.toggleStep(5)
        assertEquals(2, engine.getStep(0, 3, 5))
    }

    @Test
    fun songModeChainsTappedPatterns() {
        val (engine, drums) = make()
        drums.changeSongMode(true)
        // Boom Bap's song is ready to play.
        assertContentEquals(intArrayOf(0, 0, 0, 1, 0, 0, 0, 2), engine.lastChain)
        drums.clearSong()
        assertContentEquals(intArrayOf(0), engine.lastChain) // empty song: the pattern being edited
        drums.selectPattern(1)
        drums.selectPattern(1)
        drums.selectPattern(0)
        assertContentEquals(intArrayOf(1, 1, 0), engine.lastChain)
        drums.removeLastFromSong()
        assertContentEquals(intArrayOf(1, 1), engine.lastChain)
        drums.changeSongMode(false)
        assertContentEquals(intArrayOf(0), engine.lastChain)
    }

    @Test
    fun padsPlayOrRecordWhenRecIsOnAndPlaying() {
        val (engine, drums) = make()
        drums.hitPad(2)
        assertEquals(2 to 0.8f, engine.triggers.last())
        assertEquals(2, drums.selectedTrack)
        drums.recordMode = true
        drums.hitPad(4) // not playing: just plays
        assertEquals(4, engine.triggers.last().first)
        drums.play()
        drums.accentMode = true
        drums.hitPad(4)
        assertEquals(4 to true, engine.recorded.last())
    }

    @Test
    fun tapTempoFollowsTheTaps() {
        var now = 0.0
        val (_, drums) = make { now }
        for (i in 0 until 4) {
            now = i * 0.5 // 120 BPM
            drums.tapTempo()
        }
        assertEquals(120f, drums.tempo)
        now = 10.0 // a long pause starts over
        drums.tapTempo()
        assertEquals(120f, drums.tempo)
    }

    @Test
    fun copyClearAndLength() {
        val (engine, drums) = make()
        drums.copyPattern(5)
        assertEquals(drums.step(0, 0, 0), drums.step(5, 0, 0))
        assertTrue(drums.patternHasSteps(5))
        drums.selectPattern(5)
        drums.clearPattern()
        assertFalse(drums.patternHasSteps(5))
        assertEquals(0, engine.getStep(5, 0, 0))
        drums.setLength(40)
        assertEquals(16, drums.lengths[5])
    }

    @Test
    fun pollFollowsTheEngineAndCopiesBackRecordedSteps() {
        val (engine, drums) = make()
        engine.pollAnswer = intArrayOf(7, 1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 9)
        drums.poll()
        assertEquals(7, drums.currentStep)
        assertEquals(1, drums.playingPattern)
        assertEquals(3, drums.songPosition)
        assertEquals(9, drums.hits[9])
        // The engine recorded a hit: its edit count moves and the model copies it in.
        engine.steps[Triple(0, 1, 2)] = 1
        engine.pollAnswer[3] = 1
        drums.poll()
        assertEquals(1, drums.step(0, 1, 2))
    }

    @Test
    fun beatsParseTheirText() {
        assertEquals(listOf(2, 0, 1, 0), Beat.parse("X. x."))
        assertEquals(16, Beat.parse("x.x. x.x. x.x. x.x.").size)
    }
}
