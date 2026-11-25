//
//  DrumMachine.kt
//
//  The drum machine as the screen sees it: patterns, kit settings, tempo,
//  transport and what's playing right now. Ported from DrumMachine.swift.
//
//  Pattern steps are kept here *and* in the engine. The screen reads this
//  copy; every edit is written through to the engine, which does the actual
//  timing on the audio thread.
//

package com.tuesdaygames.synth.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tuesdaygames.synth.engine.SoundEngine
import com.tuesdaygames.synth.engine.Sizes
import kotlin.math.roundToInt

/** What each drum is called and its knobs' names and defaults, from the engine. */
data class TrackInfo(
    val name: String,
    val shortName: String,
    val paramNames: List<String>,
    val defaults: List<Float>,
)

class DrumMachine(
    private val engine: SoundEngine,
    /** The kit's names and defaults; the app reads them from the engine, tests pass their own. */
    val tracks: List<TrackInfo> = Kit.fromEngine(),
    /** Seconds since some fixed point, for tap tempo. */
    private val clock: () -> Double = { System.nanoTime() / 1e9 },
) {
    companion object {
        const val TRACKS = Sizes.DRUM_TRACKS
        const val STEPS = Sizes.STEPS
        const val PATTERNS = Sizes.PATTERNS
        const val MAX_SONG = Sizes.MAX_SONG
        val patternNames = listOf("A", "B", "C", "D", "E", "F", "G", "H")

        /** Level, tune, decay, tone: the engine's se_drum_param order. */
        const val SOUND_PARAMS = Sizes.DRUM_PARAMS
        const val TUNE = 1

        private fun index(pattern: Int, track: Int, step: Int) = (pattern * TRACKS + track) * STEPS + step
    }

    // Patterns: one flat list of 0 off, 1 on, 2 accent.
    private val steps = mutableStateListOf<Int>().apply { repeat(PATTERNS * TRACKS * STEPS) { add(0) } }
    val lengths = mutableStateListOf<Int>().apply { repeat(PATTERNS) { add(STEPS) } }
    private val sound = mutableStateListOf<Float>().apply { tracks.forEach { addAll(it.defaults) } }

    // Editing state
    var selectedTrack by mutableIntStateOf(0)
    var accentMode by mutableStateOf(false)
    var recordMode by mutableStateOf(false)
    var editPattern by mutableIntStateOf(0)
        private set
    var songMode by mutableStateOf(false)
        private set
    val song = mutableStateListOf<Int>()
    var beatName by mutableStateOf("")
        private set

    // Groove & transport
    var tempo by mutableFloatStateOf(120f)
        private set
    var swing by mutableFloatStateOf(0f)
        private set
    var volume by mutableFloatStateOf(0.8f)
        private set
    var isPlaying by mutableStateOf(false)
        private set

    // Live readouts from the engine
    var currentStep by mutableIntStateOf(-1)
        private set
    var playingPattern by mutableIntStateOf(0)
        private set
    var songPosition by mutableIntStateOf(0)
        private set
    val hits = mutableStateListOf<Int>().apply { repeat(TRACKS) { add(0) } }

    private var lastEditCount = 0
    private val pollBuffer = IntArray(4 + TRACKS)
    private val tapTimes = ArrayDeque<Double>()

    init {
        load(Beat.factory[0])
    }

    // Steps

    fun step(track: Int, step: Int) = steps[index(editPattern, track, step)]
    fun step(pattern: Int, track: Int, step: Int) = steps[index(pattern, track, step)]

    fun hasSteps(track: Int) = (0 until STEPS).any { steps[index(editPattern, track, it)] > 0 }

    fun patternHasSteps(pattern: Int) =
        (0 until TRACKS).any { t -> (0 until STEPS).any { s -> steps[index(pattern, t, s)] > 0 } }

    /** Tap a step: on/off, or accent on/off when ACCENT is lit. */
    fun toggleStep(step: Int) {
        val current = step(selectedTrack, step)
        val next = if (accentMode) (if (current == 2) 1 else 2) else (if (current == 0) 1 else 0)
        setStep(editPattern, selectedTrack, step, next)
    }

    private fun setStep(pattern: Int, track: Int, step: Int, value: Int) {
        steps[index(pattern, track, step)] = value
        engine.setStep(pattern, track, step, value)
    }

    fun clearTrack() {
        for (s in 0 until STEPS) setStep(editPattern, selectedTrack, s, 0)
    }

    fun clearPattern() {
        for (t in 0 until TRACKS) for (s in 0 until STEPS) setStep(editPattern, t, s, 0)
    }

    fun copyPattern(target: Int) {
        if (target == editPattern) return
        for (t in 0 until TRACKS) for (s in 0 until STEPS) setStep(target, t, s, step(editPattern, t, s))
        setLength(lengths[editPattern], target)
    }

    fun setLength(length: Int, pattern: Int = editPattern) {
        lengths[pattern] = length.coerceIn(1, STEPS)
        engine.setLength(pattern, lengths[pattern])
    }

    // Patterns & song

    /** Choose a pattern to edit and play. In song mode it's also added to the song. */
    fun selectPattern(pattern: Int) {
        editPattern = pattern
        if (songMode && song.size < MAX_SONG) song.add(pattern)
        pushChain()
    }

    fun changeSongMode(on: Boolean) {
        songMode = on
        pushChain()
    }

    fun removeLastFromSong() {
        if (song.isEmpty()) return
        song.removeAt(song.lastIndex)
        pushChain()
    }

    fun clearSong() {
        song.clear()
        pushChain()
    }

    private fun pushChain() {
        val chain = if (songMode && song.isNotEmpty()) song.toIntArray() else intArrayOf(editPattern)
        engine.setChain(chain)
    }

    // Sound

    fun soundValue(track: Int, param: Int) = sound[track * SOUND_PARAMS + param]

    fun setSound(track: Int, param: Int, value: Float) {
        sound[track * SOUND_PARAMS + param] = value
        engine.drumSetParam(track, param, value)
    }

    fun changeTempo(bpm: Float) {
        tempo = bpm.roundToInt().toFloat().coerceIn(40f, 240f)
        engine.setTempo(tempo)
    }

    fun changeSwing(amount: Float) {
        swing = amount.coerceIn(0f, 1f)
        engine.setSwing(swing)
    }

    fun changeVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        engine.drumSetVolume(volume)
    }

    /** Tap along to the beat; the tempo follows your taps. */
    fun tapTempo() {
        val now = clock()
        if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2) tapTimes.clear()
        tapTimes.addLast(now)
        while (tapTimes.size > 5) tapTimes.removeFirst()
        if (tapTimes.size < 2) return
        val intervals = tapTimes.zipWithNext { a, b -> b - a }
        changeTempo((60 / intervals.average()).toFloat())
    }

    // Playing

    fun togglePlay() = if (isPlaying) stop() else play()

    fun play() {
        engine.play()
        isPlaying = true
    }

    fun stop() {
        engine.stop()
        isPlaying = false
    }

    /** A finger on a pad. Records into the pattern when REC is on and it's playing. */
    fun hitPad(track: Int) {
        selectedTrack = track
        if (recordMode && isPlaying) {
            engine.recordHit(track, accentMode)
        } else {
            engine.drumTrigger(track, if (accentMode) 1.0f else 0.8f)
        }
    }

    // Premade beats

    fun load(beat: Beat) {
        for (p in 0 until PATTERNS) {
            val rows = beat.patterns.getOrNull(p) ?: emptyMap()
            for (t in 0 until TRACKS) {
                val values = Beat.parse(rows[tracks[t].shortName] ?: "")
                for (s in 0 until STEPS) setStep(p, t, s, values.getOrElse(s) { 0 })
            }
            setLength(STEPS, p)
        }
        for (t in 0 until TRACKS) {
            val tweaks = beat.kit[tracks[t].shortName] ?: emptyMap()
            for (i in 0 until SOUND_PARAMS) setSound(t, i, tweaks[i] ?: tracks[t].defaults[i])
        }
        changeTempo(beat.tempo)
        changeSwing(beat.swing)
        song.clear()
        song.addAll(beat.song)
        songMode = false
        editPattern = 0
        selectedTrack = 0
        beatName = beat.name
        pushChain()
    }

    // Following the engine (called once per frame)

    fun poll() {
        engine.poll(pollBuffer)
        // Only assign when something changed, so the screen redraws only then.
        if (pollBuffer[0] != currentStep) currentStep = pollBuffer[0]
        if (pollBuffer[1] != playingPattern) playingPattern = pollBuffer[1]
        if (pollBuffer[2] != songPosition) songPosition = pollBuffer[2]
        for (t in 0 until TRACKS) if (pollBuffer[4 + t] != hits[t]) hits[t] = pollBuffer[4 + t]

        // Live recording changed steps inside the engine: copy them back.
        if (pollBuffer[3] != lastEditCount) {
            lastEditCount = pollBuffer[3]
            for (p in 0 until PATTERNS) for (t in 0 until TRACKS) for (s in 0 until STEPS) {
                val v = engine.getStep(p, t, s)
                if (steps[index(p, t, s)] != v) steps[index(p, t, s)] = v
            }
        }
    }
}

/** The kit's names and knob defaults, read from the engine. */
object Kit {
    fun fromEngine(): List<TrackInfo> {
        val e = com.tuesdaygames.synth.engine.NativeEngine
        return (0 until Sizes.DRUM_TRACKS).map { t ->
            TrackInfo(
                name = e.drumName(t),
                shortName = e.drumShortName(t),
                paramNames = (0 until Sizes.DRUM_PARAMS).map { e.drumParamName(t, it) },
                defaults = (0 until Sizes.DRUM_PARAMS).map { e.drumParamDefault(t, it) },
            )
        }
    }
}
