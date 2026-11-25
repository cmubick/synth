//
//  MainActivity.kt
//
//  Starts the sound engine and the store, keeps the app full screen in
//  landscape, and shows the Compose screens (ui/App.kt). The counterpart of
//  SynthApp.swift.
//

package com.tuesdaygames.synth

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tuesdaygames.synth.engine.NativeEngine
import com.tuesdaygames.synth.engine.NativeSoundEngine
import com.tuesdaygames.synth.engine.Sizes
import com.tuesdaygames.synth.haptics.Haptics
import com.tuesdaygames.synth.model.DrumMachine
import com.tuesdaygames.synth.model.SynthPatch
import com.tuesdaygames.synth.store.PlayStore
import com.tuesdaygames.synth.store.StoreModel
import com.tuesdaygames.synth.ui.App

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var patch: SynthPatch
    private lateinit var drums: DrumMachine
    private lateinit var store: StoreModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the bars and the camera cutout; the walnut panels fill those edges.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        prefs = Prefs(this)
        Haptics.setUp(this, prefs)
        Sizes.checkAgainstEngine()

        // Debug builds take the same switch as iOS's -unlocked launch argument:
        //   adb shell am start -n com.tuesdaygames.synth/.MainActivity --ez unlocked true
        val override = if (BuildConfig.DEBUG && intent.hasExtra("unlocked")) intent.getBooleanExtra("unlocked", false) else null
        store = StoreModel(PlayStore(this, prefs, override))
        patch = SynthPatch(NativeSoundEngine)
        drums = DrumMachine(NativeSoundEngine)
        store.start()

        setContent {
            App(
                patch = patch,
                drums = drums,
                store = store,
                prefs = prefs,
                activity = this,
                keepScreenOn = ::setKeepScreenOn,
            )
        }
        // Full screen like the iPhone app (and Skate): bars come back with a swipe.
        hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        NativeEngine.startAudio()
        if (::store.isInitialized) store.refresh()
    }

    override fun onStop() {
        // Like the iPhone app, the music stops when you leave the app.
        NativeEngine.allNotesOff()
        NativeEngine.stopAudio()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /** Don't let the phone dim and lock in the middle of a beat. */
    private fun setKeepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
