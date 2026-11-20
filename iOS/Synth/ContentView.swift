import SwiftUI

struct ContentView: View {
    @Environment(SynthPatch.self) private var patch
    @Environment(\.scenePhase) private var scenePhase
    @State private var octave = 3 // lowest key is C3

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let woodLeading = max(insets.leading, 18)
            let woodTrailing = max(insets.trailing, 18)
            let height = geo.size.height
            let panelHeight = max(150, height * 0.47)

            ZStack {
                // Walnut side panels sit in the screen's unsafe edges.
                HStack(spacing: 0) {
                    Rectangle().fill(Theme.wood).frame(width: woodLeading)
                    Theme.panel
                    Rectangle().fill(Theme.wood).frame(width: woodTrailing)
                }
                .ignoresSafeArea()

                VStack(spacing: 8) {
                    TopBar()
                        .frame(height: 40)

                    SynthPanel()
                        .frame(height: panelHeight)
                        .environment(\.knobSize, knobSize(panelHeight: panelHeight))
                        .environment(\.faderHeight, panelHeight - 58)

                    HStack(spacing: 8) {
                        OctaveControl(octave: $octave)
                        KeyboardView(
                            lowestNote: (octave + 1) * 12,
                            onNoteOn: { patch.audio.noteOn($0, velocity: $1) },
                            onNoteOff: { patch.audio.noteOff($0) }
                        )
                        .background(Color.black)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                    }
                }
                .padding(.leading, woodLeading - insets.leading + 8)
                .padding(.trailing, woodTrailing - insets.trailing + 8)
                .padding(.vertical, 6)
            }
        }
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
        .defersSystemGestures(on: .all)
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { patch.audio.allNotesOff() }
        }
    }

    private func knobSize(panelHeight: CGFloat) -> CGFloat {
        // Two rows of knobs plus labels and the module title fit the panel.
        min(64, max(34, (panelHeight - 60) / 2 - 22))
    }
}

struct TopBar: View {
    @Environment(SynthPatch.self) private var patch

    var body: some View {
        HStack(spacing: 12) {
            Text("SYNTH")
                .font(Theme.titleFont(18))
                .tracking(4)
                .foregroundStyle(Theme.cream)

            PresetPicker()

            Spacer(minLength: 8)

            SegmentSwitch(id: SE_VOICE_MODE, labels: ["POLY", "MONO"])

            HStack(spacing: 6) {
                Image(systemName: "speaker.wave.2.fill")
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.label)
                VolumeSlider()
                    .frame(width: 110)
            }
        }
    }
}

struct PresetPicker: View {
    @Environment(SynthPatch.self) private var patch

    var body: some View {
        HStack(spacing: 2) {
            stepButton("chevron.left", by: -1)
            Menu {
                ForEach(Preset.factory.indices, id: \.self) { i in
                    Button {
                        patch.load(presetAt: i)
                    } label: {
                        if i == patch.presetIndex {
                            Label(Preset.factory[i].name, systemImage: "checkmark")
                        } else {
                            Text(Preset.factory[i].name)
                        }
                    }
                }
            } label: {
                Text(patch.presetName)
                    .font(Theme.titleFont(13))
                    .foregroundStyle(Theme.accent)
                    .frame(minWidth: 110)
                    .padding(.vertical, 7)
            }
            stepButton("chevron.right", by: 1)
        }
        .padding(.horizontal, 2)
        .background(RoundedRectangle(cornerRadius: 7).fill(Color.black.opacity(0.5)))
        .overlay(RoundedRectangle(cornerRadius: 7).stroke(Theme.moduleEdge))
    }

    private func stepButton(_ symbol: String, by step: Int) -> some View {
        Button {
            patch.load(presetAt: patch.presetIndex + step)
        } label: {
            Image(systemName: symbol)
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Theme.label)
                .frame(width: 28, height: 30)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(step < 0 ? "Previous sound" : "Next sound")
    }
}

struct VolumeSlider: View {
    @Environment(SynthPatch.self) private var patch

    var body: some View {
        Slider(value: Binding(get: { patch[SE_MASTER_VOLUME] },
                              set: { patch[SE_MASTER_VOLUME] = $0 }),
               in: 0...1)
            .tint(Theme.accent)
            .accessibilityLabel("Volume")
    }
}

struct OctaveControl: View {
    @Binding var octave: Int

    var body: some View {
        VStack(spacing: 6) {
            button("plus", enabled: octave < 6) { octave += 1 }
            VStack(spacing: 0) {
                Text("OCT")
                    .font(Theme.titleFont(9))
                    .foregroundStyle(Theme.label)
                Text("C\(octave)")
                    .font(Theme.titleFont(15))
                    .foregroundStyle(Theme.accent)
                    .monospacedDigit()
            }
            button("minus", enabled: octave > 0) { octave -= 1 }
        }
        .frame(width: 50)
        .frame(maxHeight: .infinity)
        .background(RoundedRectangle(cornerRadius: 8).fill(Theme.module))
    }

    private func button(_ symbol: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(enabled ? Theme.cream : Theme.label.opacity(0.3))
                .frame(width: 40, height: 34)
                .background(RoundedRectangle(cornerRadius: 6).fill(Theme.button))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(symbol == "plus" ? "Octave up" : "Octave down")
    }
}
