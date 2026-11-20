import SwiftUI

/// The front panel: every sound control, grouped like a hardware synth.
/// Scrolls sideways on phones; drag on a module's background to scroll.
struct SynthPanel: View {
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: 8) {
                Module("OSCILLATORS") {
                    VStack(spacing: 8) {
                        HStack(spacing: 10) {
                            WaveSelector(id: SE_OSC1_WAVE, title: "Osc 1")
                            WaveSelector(id: SE_OSC2_WAVE, title: "Osc 2")
                        }
                        HStack(spacing: 6) {
                            Knob(id: SE_OSC2_SEMI)
                            Knob(id: SE_OSC2_FINE)
                            Knob(id: SE_GLIDE)
                        }
                    }
                }

                Module("MIXER") {
                    VStack(spacing: 8) {
                        Knob(id: SE_OSC_MIX, label: "Osc 1 ↔ 2")
                        HStack(spacing: 6) {
                            Knob(id: SE_SUB_LEVEL)
                            Knob(id: SE_NOISE_LEVEL)
                        }
                    }
                }

                Module("FILTER") {
                    VStack(spacing: 8) {
                        HStack(spacing: 10) {
                            Knob(id: SE_FILTER_CUTOFF, scale: 1.15)
                            Knob(id: SE_FILTER_RESONANCE, scale: 1.15)
                        }
                        HStack(spacing: 6) {
                            Knob(id: SE_FILTER_ENV_AMOUNT)
                            Knob(id: SE_FILTER_KEYTRACK)
                            Knob(id: SE_FILTER_DRIVE)
                        }
                    }
                }

                Module("FILTER ENVELOPE") {
                    HStack(spacing: 8) {
                        Fader(id: SE_FENV_ATTACK, label: "A")
                        Fader(id: SE_FENV_DECAY, label: "D")
                        Fader(id: SE_FENV_SUSTAIN, label: "S")
                        Fader(id: SE_FENV_RELEASE, label: "R")
                    }
                }

                Module("AMP ENVELOPE") {
                    HStack(spacing: 8) {
                        Fader(id: SE_AENV_ATTACK, label: "A")
                        Fader(id: SE_AENV_DECAY, label: "D")
                        Fader(id: SE_AENV_SUSTAIN, label: "S")
                        Fader(id: SE_AENV_RELEASE, label: "R")
                    }
                }

                Module("LFO") {
                    VStack(spacing: 8) {
                        WaveSelector(id: SE_LFO_WAVE, title: "Shape",
                                     shapes: [.sine, .triangle, .square, .random])
                        HStack(spacing: 6) {
                            Knob(id: SE_LFO_RATE)
                            Knob(id: SE_LFO_TO_PITCH)
                            Knob(id: SE_LFO_TO_FILTER)
                        }
                    }
                }

                Module("ECHO") {
                    VStack(spacing: 8) {
                        HStack(spacing: 6) {
                            Knob(id: SE_DELAY_TIME)
                            Knob(id: SE_DELAY_FEEDBACK)
                        }
                        Knob(id: SE_DELAY_MIX, label: "Amount")
                    }
                }
            }
            .padding(.horizontal, 2)
        }
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
