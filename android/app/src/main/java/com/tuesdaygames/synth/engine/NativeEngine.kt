//
//  NativeEngine.kt
//
//  The shared C++ sound engine (../Engine, the same code the iOS app runs),
//  reached through JNI (app/src/main/cpp/JniBridge.cpp). Every function here
//  is one call of the engine's C API in Engine/include/synth_engine.h.
//
//  The rest of the app talks to `SoundEngine`, so tests can use a pretend one.
//

package com.tuesdaygames.synth.engine

object NativeEngine {
    init {
        System.loadLibrary("synth")
        create()
    }

    external fun create()
    external fun startAudio(): Boolean
    external fun stopAudio()
    external fun sampleRate(): Int
    external fun sizes(): IntArray

    // Synth
    external fun noteOn(note: Int, velocity: Float)
    external fun noteOff(note: Int)
    external fun allNotesOff()
    external fun setParam(id: Int, value: Float)
    external fun paramKey(id: Int): String
    external fun paramName(id: Int): String
    external fun paramRange(id: Int): FloatArray
    external fun paramFormat(id: Int, value: Float): String

    // Drums
    external fun drumName(track: Int): String
    external fun drumShortName(track: Int): String
    external fun drumParamName(track: Int, param: Int): String
    external fun drumParamDefault(track: Int, param: Int): Float
    external fun drumParamFormat(param: Int, value: Float): String
    external fun drumTrigger(track: Int, velocity: Float)
    external fun drumSetParam(track: Int, param: Int, value: Float)
    external fun drumSetVolume(volume: Float)

    // Sequencer
    external fun setStep(pattern: Int, track: Int, step: Int, value: Int)
    external fun getStep(pattern: Int, track: Int, step: Int): Int
    external fun setLength(pattern: Int, steps: Int)
    external fun setTempo(bpm: Float)
    external fun setSwing(amount: Float)
    external fun setChain(patterns: IntArray)
    external fun play()
    external fun stop()
    external fun recordHit(track: Int, accent: Boolean)
    external fun poll(out: IntArray)
}

/** What the screens and models need from the engine. */
interface SoundEngine {
    fun noteOn(note: Int, velocity: Float)
    fun noteOff(note: Int)
    fun allNotesOff()
    fun setParam(id: Int, value: Float)

    fun drumTrigger(track: Int, velocity: Float)
    fun drumSetParam(track: Int, param: Int, value: Float)
    fun drumSetVolume(volume: Float)

    fun setStep(pattern: Int, track: Int, step: Int, value: Int)
    fun getStep(pattern: Int, track: Int, step: Int): Int
    fun setLength(pattern: Int, steps: Int)
    fun setTempo(bpm: Float)
    fun setSwing(amount: Float)
    fun setChain(patterns: IntArray)
    fun play()
    fun stop()
    fun recordHit(track: Int, accent: Boolean)

    /** Fills [current step, playing pattern, song position, edit count, hit count per drum...]. */
    fun poll(out: IntArray)
}

/** The real engine. */
object NativeSoundEngine : SoundEngine {
    private val e = NativeEngine
    override fun noteOn(note: Int, velocity: Float) = e.noteOn(note, velocity)
    override fun noteOff(note: Int) = e.noteOff(note)
    override fun allNotesOff() = e.allNotesOff()
    override fun setParam(id: Int, value: Float) = e.setParam(id, value)
    override fun drumTrigger(track: Int, velocity: Float) = e.drumTrigger(track, velocity)
    override fun drumSetParam(track: Int, param: Int, value: Float) = e.drumSetParam(track, param, value)
    override fun drumSetVolume(volume: Float) = e.drumSetVolume(volume)
    override fun setStep(pattern: Int, track: Int, step: Int, value: Int) = e.setStep(pattern, track, step, value)
    override fun getStep(pattern: Int, track: Int, step: Int) = e.getStep(pattern, track, step)
    override fun setLength(pattern: Int, steps: Int) = e.setLength(pattern, steps)
    override fun setTempo(bpm: Float) = e.setTempo(bpm)
    override fun setSwing(amount: Float) = e.setSwing(amount)
    override fun setChain(patterns: IntArray) = e.setChain(patterns)
    override fun play() = e.play()
    override fun stop() = e.stop()
    override fun recordHit(track: Int, accent: Boolean) = e.recordHit(track, accent)
    override fun poll(out: IntArray) = e.poll(out)
}

/** The engine's fixed sizes, from synth_engine.h. Checked against the native code at startup. */
object Sizes {
    const val DRUM_TRACKS = 10
    const val STEPS = 16
    const val PATTERNS = 8
    const val MAX_SONG = 64
    const val DRUM_PARAMS = 4

    fun checkAgainstEngine() {
        val native = NativeEngine.sizes()
        check(native[0] == DRUM_TRACKS && native[1] == STEPS && native[2] == PATTERNS &&
            native[3] == MAX_SONG && native[5] == DRUM_PARAMS) {
            "Kotlin sizes don't match synth_engine.h: ${native.toList()}"
        }
    }
}
