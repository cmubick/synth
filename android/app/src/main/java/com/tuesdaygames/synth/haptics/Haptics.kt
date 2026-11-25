//
//  Haptics.kt
//
//  Light taps under your finger: a tick for steps and patterns, a tap for
//  pads, a firmer one for play/stop. Ported from iOS's Haptics.swift, with
//  the Android side copied from Skate's Haptics.kt (vibration primitives on
//  Android 11+, predefined effects on 10, short buzzes before that).
//  Can be turned off in More ("haptics", on by default, as on iOS).
//

package com.tuesdaygames.synth.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.tuesdaygames.synth.KeyValueStore
import com.tuesdaygames.synth.Prefs

object Haptics {
    private var vibrator: Vibrator? = null
    private var prefs: KeyValueStore? = null

    fun setUp(context: Context, prefs: KeyValueStore) {
        this.prefs = prefs
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    val isOn: Boolean get() = prefs?.getBoolean(Prefs.HAPTICS, true) ?: true

    /** The faintest tick: a step or a pattern button. */
    fun tick() = play(Kind.Tick, 0.6f)

    /** A light tap: hitting a pad. */
    fun tap() = play(Kind.Light, 0.8f)

    /** A firmer tap: play / stop, a tip going through. */
    fun thump() = play(Kind.Rigid, 1f)

    private enum class Kind { Light, Tick, Rigid }

    private fun play(kind: Kind, intensity: Float) {
        if (!isOn) return
        val vibrator = vibrator ?: return
        try {
            if (!vibrator.hasVibrator()) return
            val effect = effect(vibrator, kind, intensity)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                vibrator.vibrate(effect)
            }
        } catch (error: Exception) {
            // A phone that refuses to vibrate plays without it.
        }
    }

    private fun effect(vibrator: Vibrator, kind: Kind, intensity: Float): VibrationEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val primitive = when (kind) {
                Kind.Light -> VibrationEffect.Composition.PRIMITIVE_CLICK
                Kind.Tick -> VibrationEffect.Composition.PRIMITIVE_TICK
                Kind.Rigid -> VibrationEffect.Composition.PRIMITIVE_CLICK
            }
            if (vibrator.areAllPrimitivesSupported(primitive)) {
                val scale = if (kind == Kind.Light) intensity * 0.6f else intensity
                return VibrationEffect.startComposition().addPrimitive(primitive, scale).compose()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return VibrationEffect.createPredefined(
                when (kind) {
                    Kind.Light, Kind.Tick -> VibrationEffect.EFFECT_TICK
                    Kind.Rigid -> VibrationEffect.EFFECT_CLICK
                },
            )
        }
        val length = when (kind) {
            Kind.Tick -> 8L
            Kind.Light -> 12L
            Kind.Rigid -> 15L
        }
        return VibrationEffect.createOneShot(length, (intensity * 255).toInt().coerceIn(1, 255))
    }
}
