# 808Synth — App Store listing

Started 2026-11-24. The rest of the listing (description, keywords,
screenshots, age rating) follows the same layout as Skate's and Mahjong's
`AppStore/listing.md` and isn't written yet.

## URLs and privacy

- **Support URL:** `https://cmubick.github.io/synth/` (the page is `docs/index.html`)
- **Privacy Policy URL:** `https://cmubick.github.io/synth/privacy.html`
- These go live once GitHub Pages is turned on for the repo (Settings → Pages →
  Deploy from a branch → `main`, folder `/docs`), as for Skate.
- **App Privacy:** Data Not Collected. Checked against the code: no network
  requests of its own (only Apple's StoreKit), no analytics, ads, accounts,
  links or third-party SDKs, no microphone yet. `iOS/Synth/PrivacyInfo.xcprivacy`
  declares UserDefaults (CA92.1) only. Revisit when recording (microphone) or
  saving arrives.
- **App icon:** `AppStore/icon/icon-1024.png`, made by `AppStore/make_icon.sh`.

## Before review

- The unlock screen lists features marked COMING SOON (export, saves, sampler,
  MIDI). Apple can reject apps that advertise features that aren't there yet;
  if review objects, remove the `available: false` entries from
  `FullVersion.features` in `Store.swift` until they ship.

---

## In-app purchases

Create these under **Monetization → In-App Purchases** with exactly these
product IDs (the app looks them up by ID). Names and wording match Sevens, Skate
and Mahjong; the tip jar shows SMALL / GENEROUS / BIG. The local test copy is
`iOS/SynthTests/Synth.storekit`.

| Product ID | Type | Reference name | Price | Display name (30 max) | Description (45 max) |
|---|---|---|---|---|---|
| `synth.fullgame` | Non-consumable | Full Version | $4.99 | Full Version [12] | Song mode, patterns C to H and every beat. [42] |
| `synth.tip.small` | Consumable | Small tip | $0.99 | Small tip [9] | A small thank-you to Tuesday Games. [35] |
| `synth.tip.medium` | Consumable | Generous tip | $2.99 | Generous tip [12] | A generous thank-you to Tuesday Games. [38] |
| `synth.tip.large` | Consumable | Big tip | $4.99 | Big tip [7] | A very generous thank-you to Tuesday Games. [43] |

- **Full Version** is Family Shareable; tips aren't.
- **Review notes, Full Version:** "Tap the ☰ button at the top right to open
  More, then Unlock. Or go to the DRUMS tab and tap SONG, pattern C to H, or a
  locked beat in the beat menu (each shows a lock). After buying: SONG turns on
  song mode (tapping pattern letters adds them to the song, which plays them in
  order), patterns C to H can be picked and edited, and Electro Funk, Half-Time
  Boom and Garage Rock load from the beat menu. Restore purchases and Redeem
  code are under the Unlock button."
- **Review notes, tips:** "Tap the ☰ button at the top right to open More; the
  tip jar is below the full version. Tips are optional, can be given more than
  once and unlock nothing; the screen thanks the player after a tip."
- On the version page, under **In-App Purchases and Subscriptions**, select all
  four so they're reviewed with 1.0.
- Selling anything needs the Paid Apps agreement, with banking and tax details.
- If the full version should wait for more features, set `Store.fullGameOnSale`
  to `false` (as Skate does): the unlock is then never loaded, SONG shows
  "Coming soon", and only the tips are sold.
