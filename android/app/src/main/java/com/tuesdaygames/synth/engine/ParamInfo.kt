//
//  ParamInfo.kt
//
//  A sound parameter as the screens see it. Names, ranges, defaults and value
//  text all come from the shared engine, exactly as on iOS (ParamInfo.swift).
//  Parameters are looked up by their stable key, never by number.
//

package com.tuesdaygames.synth.engine

data class ParamInfo(
    val id: Int,
    val key: String,
    val name: String,
    val min: Float,
    val max: Float,
    val default: Float,
    val steps: Int,
) {
    val isDiscrete get() = steps > 0
    val isBipolar get() = min < 0f

    fun normalized(value: Float) = (value - min) / (max - min)
    fun format(value: Float): String = NativeEngine.paramFormat(id, value)

    companion object {
        val all: List<ParamInfo> by lazy {
            val count = NativeEngine.sizes()[4]
            (0 until count).map { id ->
                val range = NativeEngine.paramRange(id)
                ParamInfo(
                    id = id,
                    key = NativeEngine.paramKey(id),
                    name = NativeEngine.paramName(id),
                    min = range[0],
                    max = range[1],
                    default = range[2],
                    steps = range[3].toInt(),
                )
            }
        }

        private val byKey by lazy { all.associateBy { it.key } }

        fun of(id: Int) = all[id]
        fun indexOf(key: String): Int? = byKey[key]?.id
        fun id(key: String): Int = indexOf(key) ?: error("No sound parameter called $key")
    }
}

/** The parameters the screens use, by key (see Engine/src/Params.cpp). */
object Param {
    val osc1Wave by lazy { ParamInfo.id("osc1.wave") }
    val osc2Wave by lazy { ParamInfo.id("osc2.wave") }
    val osc2Semi by lazy { ParamInfo.id("osc2.semi") }
    val osc2Fine by lazy { ParamInfo.id("osc2.fine") }
    val oscMix by lazy { ParamInfo.id("osc.mix") }
    val sub by lazy { ParamInfo.id("osc.sub") }
    val noise by lazy { ParamInfo.id("osc.noise") }
    val cutoff by lazy { ParamInfo.id("filter.cutoff") }
    val resonance by lazy { ParamInfo.id("filter.resonance") }
    val envAmount by lazy { ParamInfo.id("filter.env") }
    val keytrack by lazy { ParamInfo.id("filter.keytrack") }
    val drive by lazy { ParamInfo.id("filter.drive") }
    val fenvAttack by lazy { ParamInfo.id("fenv.attack") }
    val fenvDecay by lazy { ParamInfo.id("fenv.decay") }
    val fenvSustain by lazy { ParamInfo.id("fenv.sustain") }
    val fenvRelease by lazy { ParamInfo.id("fenv.release") }
    val aenvAttack by lazy { ParamInfo.id("aenv.attack") }
    val aenvDecay by lazy { ParamInfo.id("aenv.decay") }
    val aenvSustain by lazy { ParamInfo.id("aenv.sustain") }
    val aenvRelease by lazy { ParamInfo.id("aenv.release") }
    val lfoRate by lazy { ParamInfo.id("lfo.rate") }
    val lfoWave by lazy { ParamInfo.id("lfo.wave") }
    val lfoPitch by lazy { ParamInfo.id("lfo.pitch") }
    val lfoFilter by lazy { ParamInfo.id("lfo.filter") }
    val voiceMode by lazy { ParamInfo.id("voice.mode") }
    val glide by lazy { ParamInfo.id("voice.glide") }
    val delayTime by lazy { ParamInfo.id("delay.time") }
    val delayFeedback by lazy { ParamInfo.id("delay.feedback") }
    val delayMix by lazy { ParamInfo.id("delay.mix") }
    val volume by lazy { ParamInfo.id("master.volume") }
}
