//
//  Prefs.kt
//
//  Where the app saves things between launches: Android's SharedPreferences,
//  the counterpart of iOS's UserDefaults, under the same keys as the iPhone app.
//  Android deletes them when the app is uninstalled.
//

package com.tuesdaygames.synth

import android.content.Context
import android.content.SharedPreferences

interface KeyValueStore {
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, default: Int = 0): Int
    fun putInt(key: String, value: Int)
}

class Prefs(context: Context) : KeyValueStore {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()

    companion object {
        /** The file in the app's private folder: shared_prefs/synth.xml. */
        const val FILE_NAME = "synth"

        // The same keys as iOS.
        const val HAPTICS = "haptics"
        const val KEEP_AWAKE = "keepAwakeWhilePlaying"
        const val TIPS_GIVEN = "tipsGiven"
        const val FULL_GAME_UNLOCKED = "fullGameUnlocked"
    }
}

/** A store kept in memory only. Used by the tests. */
class MemoryStore : KeyValueStore {
    private val values = mutableMapOf<String, Any>()
    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
    override fun putInt(key: String, value: Int) { values[key] = value }
}
