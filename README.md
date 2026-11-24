# Synth

A synth and drum machine for your phone — a classic analog-style synth and an
808-style drum machine in one fun, easy app. iOS first, Android later.

## Project layout

```
Engine/              Shared C++ sound engine (used by iOS now, Android later)
  include/           The plain C API both apps call (synth_engine.h)
  src/               Synth voices, ladder filter, envelopes, LFO, echo
  src/drums/         808-style drum voices and the step sequencer
  tests/             Desktop test: checks the engine and writes a WAV
iOS/                 The iPhone / iPad app (SwiftUI)
  project.yml        Xcode project definition (XcodeGen)
  Synth/             App code: audio hookup, sounds, knobs, keyboard
```

All sound is made in `Engine/`. The apps only draw the controls and pass
notes and knob values to it, so the Android version can reuse the engine as is.

## Running the iOS app

Needs Xcode and [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`).

```bash
cd iOS
xcodegen          # regenerate Synth.xcodeproj (needed after adding/removing files)
open Synth.xcodeproj
```

Then pick an iPhone or iPad simulator (or your own device) and press Run.
To run on a real device, set your team under Signing & Capabilities and change
the bundle id in `project.yml`.

## Testing the engine

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
6. **Android** — Kotlin/Compose UI on the same engine (Oboe audio)
