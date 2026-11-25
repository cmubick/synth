# 808Synth

A synth and drum machine for your phone — a classic analog-style synth and an
808-style drum machine in one fun, easy app. A Tuesday Games app for iPhone and
iPad (`iOS/`) and Android (`android/`, see its README).

- App name: **808Synth** · bundle ID `com.tuesdaygames.synth` · team ZYW8AH3KQP
- The Xcode project and target are named `Synth` (a Swift module name can't
  start with a digit); only the name on the home screen is 808Synth.

## Project layout

```
Engine/              Shared C++ sound engine (used by iOS now, Android later)
  include/           The plain C API both apps call (synth_engine.h)
  src/               Synth voices, ladder filter, envelopes, LFO, echo
  src/drums/         808-style drum voices and the step sequencer
  tests/             Desktop test: checks the engine and writes a WAV
iOS/                 The iPhone / iPad app (SwiftUI)
  project.yml        Xcode project definition (XcodeGen)
  Synth/             App code: audio hookup, sounds, knobs, keyboard, drums
    UI/Store.swift   In-app purchases (full version + tips), same as Sevens/Skate
    UI/MoreMenu.swift  The More sheet: unlock, tip jar, settings, about
    UI/PixelArt.swift  8-bit drawing: Skate's font, boxes, the synth palette
    Assets.xcassets  The app icon
  SynthTests/        Store tests and Synth.storekit (local test products)
AppStore/            App Store listing notes, icon script and icon
docs/                Support and privacy pages (GitHub Pages)
android/             The Android app (Kotlin, Compose), on the same C++ engine; see android/README.md
BACKLOG.md           Decisions and to-dos
```

All sound is made in `Engine/`. The apps only draw the controls and pass
notes and knob values to it: the iPhone app compiles it in Xcode, and the
Android app compiles the very same files with the NDK.

## Running the iOS app

Needs Xcode and [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`).

```bash
cd iOS
xcodegen          # regenerate Synth.xcodeproj (needed after adding/removing files)
open Synth.xcodeproj
```

Then pick an iPhone or iPad simulator (or your own device) and press Run. The
team and bundle ID are already set for building onto your iPhone.

## Store, tip jar and settings

The ☰ button at the top right opens **More**:

- **Full version** (`synth.fullgame`, $4.99, one purchase): **song mode**,
  **patterns C–H** and **every premade beat**; export & share, unlimited saves,
  the full sampler and MIDI are listed as coming. Free: the whole synth and kit,
  patterns A and B, and three beats (Boom Bap, Four on the Floor, Blank).
  Locked things show a lock and open the unlock. Restore purchases and Redeem
  code sit under the Unlock button. The list and the lock checks are
  `FullVersion` in `Store.swift`; `Store.fullGameOnSale` turns the sale off
  ("Coming soon") like Skate's. A refund turns the locked features back off.
- **Tip jar**: 8-bit, like Skate's, in the synth's colors (`TipJarCard.swift`).
  SMALL / GENEROUS / BIG (`synth.tip.small/.medium/.large`, $0.99 / $2.99 /
  $4.99). Tips unlock nothing; the jar fills up as tips come in (`tipsGiven`).
- **Send feedback** (`UI/Feedback.swift`): an email to info@tuesdaygames.com,
  subject "808Synth feedback", with the app version, iOS version, device model
  and full-version status filled in. Uses Mail if it's set up, else a `mailto:`
  link (the default mail app), else shows the address to copy. The app sends
  nothing itself.
- **Settings** (saved in UserDefaults): Haptics (`haptics`, on) and Keep the
  screen on while the beat plays (`keepAwakeWhilePlaying`, on).

## Art, icon and web pages

- **Pixel art** (`UI/PixelArt.swift`, `UI/PixelViews.swift`): Skate's 5x7 font,
  its outlined boxes and the synth's palette (`SynthPalette`). Plain Swift, so
  the icon script shares it.
- **App icon**: 8-bit, drawn by `AppStore/make_icon.swift`. Run
  `AppStore/make_icon.sh` to remake it; it writes the asset catalog,
  `AppStore/icon/icon-1024.png`, `docs/icon.png` and the Android launcher icons.
- **Support and privacy pages**: `docs/index.html` and `docs/privacy.html`, for
  GitHub Pages at `https://cmubick.github.io/synth/` (Pages must be turned on
  for the repo). The app itself has no links to websites, like Skate.
- **Privacy manifest**: `iOS/Synth/PrivacyInfo.xcprivacy` (UserDefaults only).
- The unlock is cached in UserDefaults `fullGameUnlocked`; the App Store is the
  source of truth. Debug builds accept `-unlocked YES|NO` as a launch argument
  for screenshots, and show a one-line store diagnostic in More.

Purchases work in the simulator and Xcode runs without App Store Connect: the
scheme uses `iOS/SynthTests/Synth.storekit`. Product IDs, prices and review
notes for App Store Connect are in `AppStore/listing.md`.

## Testing

App tests (Swift Testing: the store against `Synth.storekit`, and the feedback email): run the Synth scheme's
tests in Xcode (⌘U), or

```bash
cd iOS && xcodebuild test -project Synth.xcodeproj -scheme Synth -destination 'platform=iOS Simulator,name=iPhone 17'
```

Engine tests:

```bash
Engine/tests/run_tests.sh
```

Builds the engine for your Mac, checks levels, stability and speed, and writes
`Engine/build/engine_test.wav` so you can hear it.

## Roadmap

1. **Synth** — oscillators, ladder filter, envelopes, LFO, echo, keyboard, built-in sounds ✓
2. **Drum machine** — 808-style synthesized kit, 16-step sequencer, tempo/swing, patterns A–H, song chains, live recording, premade beats ✓
3. **Sampler** — record from the mic or an audio interface, trim, play from pads/keys
4. **Save & export** — projects, export WAV/M4A, share
5. **Extras** — MIDI in/out and sync, premade demo tracks
6. **Android** — Kotlin/Compose UI on the same engine, AAudio output ← *first version done: synth, drums, store, More, icon*
