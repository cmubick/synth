# 808Synth for Android

The Android port of the iOS app in `../iOS`. Kotlin, Gradle (Kotlin DSL), Jetpack Compose, set up the same way as Sevens', Skate's and Mahjong's Android ports.

Published by Tuesday Games. Contact: info@tuesdaygames.com. The app's ID, which Google Play and Android use to tell apps apart, is `com.tuesdaygames.synth`, the same as the iOS bundle ID. It cannot be changed once the app is on Google Play.

- **Android 8.0 (API 26) and later**, landscape only (either way round), full screen.
- **The same sound engine as iOS, not a copy:** the C++ in `../Engine` is compiled into the app by the NDK. Every sound, drum, step and swing is the same code on both phones. The only libraries are AndroidX Activity, Compose and Google Play Billing.

## Layout

- `app/src/main/cpp/`: the native side. `CMakeLists.txt` builds `../Engine` unchanged, `AudioOutput.cpp` plays it through AAudio (Android's low-latency audio, the counterpart of `AudioEngine.swift`), and `JniBridge.cpp` passes each call from Kotlin to the engine's C API (`Engine/include/synth_engine.h`).
- `app/src/main/java/com/tuesdaygames/synth/`:
  - `engine/`: `NativeEngine.kt` (the JNI functions), `SoundEngine` (what the rest of the app uses, so tests can pretend), and `ParamInfo.kt` (parameter names, ranges and value text, read from the engine by key, as on iOS).
  - `model/`: `SynthPatch.kt`, `DrumMachine.kt`, `Preset.kt` and `Beat.kt`, ported from the Swift files of the same names. `Preset.kt` and `Beat.kt` were generated from the Swift, so both apps ship the same sounds and beats.
  - `ui/`: the Compose screens, file for file with iOS: `App.kt` (ContentView), `TopBar.kt`, `SynthPanel.kt`, `Knob.kt`, `Controls.kt`, `Keyboard.kt`, `DrumScreen.kt`, `MoreSheet.kt`, `TipJarCard.kt`, and the 8-bit art in `PixelArt.kt` / `PixelViews.kt` (Skate's font and boxes, the synth's colors).
  - `store/`: `Store.kt` is what the app knows about purchases, `PlayStore.kt` talks to Google Play (copied from Skate's, with Restore and Redeem from Mahjong's), and `FullVersion.kt` says what's locked.
  - `haptics/Haptics.kt` (from Skate's), `Feedback.kt`, `Prefs.kt` (saving) and `MainActivity.kt`.
- `gradle/libs.versions.toml` holds every version number in one place, including the NDK and CMake.
- The launcher icon (`res/mipmap-*`) is drawn by `../AppStore/make_icon.swift` from the same pixels as the iOS icon.

## Running it from Android Studio

1. In Android Studio choose **Open** and pick this `android` folder (not the repository root). Wait for the Gradle sync to finish.
2. The first build needs the **NDK** and **CMake** named in `gradle/libs.versions.toml`. If Android Studio asks, let it install them (or: Settings → Languages & Frameworks → Android SDK → SDK Tools → NDK (Side by side) and CMake).
3. Pick an emulator (the **Synth Phone** device was made for this app) or a phone plugged in, and press the green **Run** arrow.

## Command line

Use Android Studio's own Java so the build does not depend on whatever Java the Mac has:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest   # the app's tests (no phone needed)
./gradlew assembleDebug            # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug             # installs it on a running emulator or plugged-in device
```

`local.properties` tells Gradle where the Android SDK is. It is specific to this Mac and is not committed; Android Studio recreates it if it is missing.

### Debug switches

Debug builds take the same switch as the iOS `-unlocked` launch argument, as an intent extra:

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB shell am start -n com.tuesdaygames.synth/.MainActivity --ez unlocked true    # pretend the full version is owned (false: not owned)
```

Close the app first (`$ADB shell am force-stop com.tuesdaygames.synth`) or the one already running carries on. Debug builds also show a one-line store diagnostic at the bottom of More.

## Tests

- **The app** (`app/src/test`): the drum machine model against a pretend engine (steps, accents, songs, pads and recording, tap tempo, copy/clear/length, following the engine).
- **Parity with iOS** (`ParityTest.kt`): reads the iPhone app's Swift files and checks Android has the same product IDs, full-version features and free patterns, premade beats, songs and free beats, pixel font and colors, and feedback address.
- **The engine** is shared, so its tests are the ones in `../Engine/tests` (`Engine/tests/run_tests.sh`).

## Products

The same product IDs as on the App Store, set up as **one-time products** in the Play Console:

| ID | What | Price | Kind |
| --- | --- | --- | --- |
| `synth.fullgame` | Song mode, patterns C to H and every premade beat (export, unlimited saves, the full sampler and MIDI as they arrive) | $4.99 | Unlock, acknowledged |
| `synth.tip.small` / `.medium` / `.large` | Small, Generous and Big tip; unlock nothing | $0.99 / $2.99 / $4.99 | Consumed straight away so they can be bought again |

Restore purchases reads the purchases back from Google Play, and Redeem code opens the Play Store's redeem page.

## What the app saves

SharedPreferences in `shared_prefs/synth.xml`, under the same keys as iOS: `haptics`, `keepAwakeWhilePlaying`, `tipsGiven` and `fullGameUnlocked` (so the full version opens before Google Play answers). Android deletes them when the app is uninstalled. Sounds and beats aren't saved yet (that's the save & export phase, on both phones).

## Not done yet

- The Play Store listing, data safety form and signing key (see Mahjong's Android README for the steps).
- Testing on real Android phones: audio delay differs a lot between phones, and Bluetooth adds more.
