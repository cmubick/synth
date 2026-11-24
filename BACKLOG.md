# 808Synth backlog

Decisions and to-dos, newest first. The README describes what's built.

## Decisions

- **2026-11-24 — Full version contents.** Chris: put all of them in. Now:
  song mode, patterns C–H, every premade beat (free: Boom Bap, Four on the
  Floor, Blank). Coming, listed as COMING SOON: export & share, unlimited saves
  (3 free), the full sampler (1 free), MIDI.
- **2026-11-24 — Look.** 8-bit app icon (Skate's font, the synth's colors,
  walnut sides), drawn by `AppStore/make_icon.swift` from the app's own
  `PixelArt.swift`. Tip jar restyled in Skate's 8-bit style with the synth's
  colors; the jar fills with coins as tips come in.

- **2026-11-24 — Store and paywall.** Same store as the other Tuesday Games
  apps: one non-consumable `synth.fullgame` ($4.99) and three tips
  (`synth.tip.small/.medium/.large`, $0.99 / $2.99 / $4.99). Tips unlock
  nothing. **Song mode** is behind the full version (Chris: "Songs for sure").
  The full version is on sale now (`Store.fullGameOnSale = true`).
- **2026-11-24 — Name.** 808Synth, bundle ID `com.tuesdaygames.synth`
  (Android IDs can't start a part with a digit). Xcode target stays `Synth`.
- **2026-11-20 — Architecture.** Shared C++ engine (`Engine/`) behind a C API,
  native UI per platform (SwiftUI now, Kotlin/Compose + Oboe later).

## Open questions

- ~~Turn on GitHub Pages~~ Done 2026-11-24: https://cmubick.github.io/synth/ (main, /docs),
  as Skate, Sevens and Mahjong have (their repos are public too).
- Update the Tuesday Games site's 808Synth icon to match the new app icon?

## To do

- The rest of `AppStore/listing.md` (description, keywords, screenshots).
- When export, saves, the sampler and MIDI arrive: set `available: true`, add
  the free limits and locks, and update the privacy policy (microphone, saved
  files).
- Phase 3: sampler and mic recording. Phase 4: save & export. Phase 5: MIDI.
  Phase 6: Android.
- Synth parts in the sequencer (basslines), after saving exists.
