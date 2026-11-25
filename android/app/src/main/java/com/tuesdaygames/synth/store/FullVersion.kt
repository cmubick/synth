//
//  FullVersion.kt
//
//  What the full version adds, and which things are locked without it.
//  The same list and rules as `FullVersion` in iOS's Store.swift.
//
//  Free: the whole synth, the whole drum kit, patterns A and B, three premade beats.
//

package com.tuesdaygames.synth.store

import com.tuesdaygames.synth.model.Beat

object FullVersion {
    data class Feature(val title: String, val detail: String, val available: Boolean)

    val features = listOf(
        Feature("Song mode", "Chain patterns into whole songs, in any order.", true),
        Feature("Patterns C to H", "Six more patterns for verses, choruses and fills. A and B are free.", true),
        Feature("Every premade beat", "All the premade beats and their songs, not just the free three.", true),
        Feature("Export and share", "Save your songs as audio and send them anywhere.", false),
        Feature("Unlimited saves", "Keep as many projects and sounds as you like. Three are free.", false),
        Feature("The full sampler", "Record your own sounds into every pad and key. One is free.", false),
        Feature("MIDI", "Play from a MIDI keyboard and sync the tempo with other gear.", false),
    )

    /** Patterns A and B are free. */
    const val FREE_PATTERN_COUNT = 2

    fun isLocked(pattern: Int, owned: Boolean) = pattern >= FREE_PATTERN_COUNT && !owned
    fun isLocked(beat: Beat, owned: Boolean) = !beat.isFree && !owned
}
