/// A named sound. Only values that differ from the defaults are listed;
/// keys are the engine's stable parameter keys, so the same format can be
/// saved to files later and loaded on Android.
struct Preset {
    let name: String
    let values: [String: Float]

    static let factory: [Preset] = [
        Preset(name: "Init", values: [:]),

        Preset(name: "Fat Bass", values: [
            "voice.mode": 1, "voice.glide": 0.15,
            "osc1.wave": 0, "osc2.wave": 1, "osc2.semi": -12, "osc2.fine": 5, "osc.mix": 0.45,
            "osc.sub": 0.6,
            "filter.cutoff": 0.33, "filter.resonance": 0.3, "filter.env": 0.45, "filter.drive": 0.45,
            "fenv.attack": 0, "fenv.decay": 0.62, "fenv.sustain": 0.2, "fenv.release": 0.5,
            "aenv.attack": 0, "aenv.decay": 0.65, "aenv.sustain": 0.85, "aenv.release": 0.45,
        ]),

        Preset(name: "Acid Line", values: [
            "voice.mode": 1, "voice.glide": 0.22,
            "osc1.wave": 0, "osc.mix": 0,
            "filter.cutoff": 0.24, "filter.resonance": 0.82, "filter.env": 0.7, "filter.drive": 0.6,
            "fenv.attack": 0, "fenv.decay": 0.56, "fenv.sustain": 0, "fenv.release": 0.5,
            "aenv.attack": 0, "aenv.decay": 0.7, "aenv.sustain": 0.7, "aenv.release": 0.35,
            "delay.time": 0.5, "delay.feedback": 0.35, "delay.mix": 0.18,
        ]),

        Preset(name: "Brass Lead", values: [
            "osc1.wave": 0, "osc2.wave": 0, "osc2.fine": 9, "osc.mix": 0.5,
            "filter.cutoff": 0.42, "filter.resonance": 0.15, "filter.env": 0.45,
            "fenv.attack": 0.55, "fenv.decay": 0.68, "fenv.sustain": 0.55, "fenv.release": 0.55,
            "aenv.attack": 0.35, "aenv.decay": 0.6, "aenv.sustain": 0.9, "aenv.release": 0.5,
            "lfo.rate": 0.62, "lfo.pitch": 0.12,
            "delay.mix": 0.12,
        ]),

        Preset(name: "Warm Pad", values: [
            "osc1.wave": 0, "osc2.wave": 2, "osc2.semi": 12, "osc2.fine": 12, "osc.mix": 0.45,
            "filter.cutoff": 0.46, "filter.resonance": 0.18, "filter.env": 0.2,
            "fenv.attack": 0.75, "fenv.decay": 0.8, "fenv.sustain": 0.5, "fenv.release": 0.8,
            "aenv.attack": 0.78, "aenv.decay": 0.7, "aenv.sustain": 0.9, "aenv.release": 0.82,
            "lfo.rate": 0.28, "lfo.filter": 0.12,
            "delay.time": 0.6, "delay.feedback": 0.45, "delay.mix": 0.3,
        ]),

        Preset(name: "Pluck", values: [
            "osc1.wave": 1, "osc2.wave": 0, "osc2.semi": 12, "osc.mix": 0.3,
            "filter.cutoff": 0.3, "filter.resonance": 0.35, "filter.env": 0.65,
            "fenv.attack": 0, "fenv.decay": 0.55, "fenv.sustain": 0, "fenv.release": 0.5,
            "aenv.attack": 0, "aenv.decay": 0.66, "aenv.sustain": 0, "aenv.release": 0.6,
            "delay.time": 0.42, "delay.feedback": 0.45, "delay.mix": 0.35,
        ]),

        Preset(name: "Wobble", values: [
            "voice.mode": 1, "voice.glide": 0.1,
            "osc1.wave": 0, "osc2.wave": 1, "osc2.semi": -12, "osc.mix": 0.5, "osc.sub": 0.4,
            "filter.cutoff": 0.3, "filter.resonance": 0.55, "filter.env": 0, "filter.drive": 0.5,
            "lfo.rate": 0.45, "lfo.wave": 0, "lfo.filter": 0.65,
            "aenv.attack": 0.1, "aenv.sustain": 1, "aenv.release": 0.4,
        ]),

        Preset(name: "Sci-Fi Bleeps", values: [
            "osc1.wave": 3, "osc2.wave": 1, "osc2.semi": 7, "osc.mix": 0.35,
            "filter.cutoff": 0.55, "filter.resonance": 0.6, "filter.env": 0.3,
            "lfo.rate": 0.7, "lfo.wave": 3, "lfo.pitch": 0.35, "lfo.filter": 0.3,
            "aenv.attack": 0, "aenv.decay": 0.6, "aenv.sustain": 0.3, "aenv.release": 0.6,
            "delay.time": 0.3, "delay.feedback": 0.55, "delay.mix": 0.4,
        ]),

        Preset(name: "Sine Sub", values: [
            "voice.mode": 1, "voice.glide": 0.12,
            "osc1.wave": 3, "osc.mix": 0, "osc.sub": 0.25,
            "filter.cutoff": 0.7, "filter.resonance": 0, "filter.env": 0, "filter.drive": 0.35,
            "aenv.attack": 0.05, "aenv.sustain": 1, "aenv.release": 0.5,
        ]),
    ]
}
