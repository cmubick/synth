//
//  SynthPatch.kt
//
//  The current sound: every knob's value. Changing a value updates the screen
//  and the engine together. Ported from SynthPatch.swift.
//

package com.tuesdaygames.synth.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.tuesdaygames.synth.engine.Param
import com.tuesdaygames.synth.engine.ParamInfo
import com.tuesdaygames.synth.engine.SoundEngine
import kotlin.math.roundToInt

class SynthPatch(val engine: SoundEngine) {
    private val values = mutableStateListOf<Float>().apply { addAll(ParamInfo.all.map { it.default }) }

    var presetIndex by mutableIntStateOf(0)
        private set

    val presetName: String get() = Preset.factory[presetIndex].name

    init {
        load(0)
    }

    operator fun get(id: Int): Float = values[id]

    operator fun set(id: Int, newValue: Float) {
        val info = ParamInfo.of(id)
        var v = newValue.coerceIn(info.min, info.max)
        if (info.isDiscrete) v = v.roundToInt().toFloat()
        if (v == values[id]) return
        values[id] = v
        engine.setParam(id, v)
    }

    fun load(index: Int) {
        val count = Preset.factory.size
        presetIndex = ((index % count) + count) % count
        val preset = Preset.factory[presetIndex]

        val newValues = ParamInfo.all.map { it.default }.toMutableList()
        for ((key, value) in preset.values) {
            val i = ParamInfo.indexOf(key) ?: error("Preset ${preset.name} uses unknown key $key")
            newValues[i] = value
        }
        // Keep the player's volume when switching sounds.
        newValues[Param.volume] = values[Param.volume]

        for (i in newValues.indices) {
            values[i] = newValues[i]
            engine.setParam(i, newValues[i])
        }
    }
}
