import SwiftUI

enum AppTab: String, CaseIterable {
    case synth = "SYNTH"
    case drums = "DRUMS"

    var symbol: String {
        switch self {
        case .synth: "pianokeys"
        case .drums: "square.grid.3x2.fill"
        }
    }
}

struct ContentView: View {
    @Environment(SynthPatch.self) private var patch
    @Environment(\.scenePhase) private var scenePhase
    @State private var octave = 3 // lowest key is C3
    @State private var tab: AppTab = .synth
    @State private var showMore = false
    @State private var moreFocus: MoreFocus?
    @Environment(DrumMachine.self) private var drums
    @AppStorage(Settings.keepAwakeKey) private var keepAwake = true

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
                    TopBar(tab: $tab)
                        .frame(height: 40)

                    switch tab {
                    case .synth:
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
                    case .drums:
                        DrumScreen()
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
        .environment(\.openMore, OpenMoreAction { focus in
            moreFocus = focus
            showMore = true
        })
        .sheet(isPresented: $showMore) {
            MoreSheet(focus: moreFocus)
                .preferredColorScheme(.dark)
        }
        // Don't let the phone dim and lock in the middle of a beat.
        .onChange(of: drums.isPlaying, initial: true) { updateIdleTimer() }
        .onChange(of: keepAwake) { updateIdleTimer() }
        // A refunded purchase turns the full-version features back off.
        .onChange(of: Store.shared.isUnlocked) { _, unlocked in
            guard !unlocked else { return }
            if drums.songMode { drums.setSongMode(false) }
            if FullVersion.isLocked(pattern: drums.editPattern) { drums.selectPattern(0) }
        }
    }

    private func updateIdleTimer() {
        UIApplication.shared.isIdleTimerDisabled = keepAwake && drums.isPlaying
    }

    private func knobSize(panelHeight: CGFloat) -> CGFloat {
        // Two rows of knobs plus labels and the module title fit the panel.
        min(64, max(34, (panelHeight - 60) / 2 - 22))
    }
}

struct TopBar: View {
    @Binding var tab: AppTab

    var body: some View {
        HStack(spacing: 6) {
            // Labels that must never be squeezed keep their size; the
            // preset/beat name and the volume slider give way on small phones.
            TabSwitch(tab: $tab)
                .fixedSize()

            switch tab {
            case .synth: PresetPicker()
            case .drums: BeatPicker()
            }

            Spacer(minLength: 4)

            if tab == .synth {
                SegmentSwitch(id: SE_VOICE_MODE, labels: ["POLY", "MONO"])
                    .fixedSize()
            }

            TransportView()
                .fixedSize()

            VolumeSlider()
                .frame(minWidth: 56, maxWidth: 80)

            MoreButton()
        }
    }
}

struct TabSwitch: View {
    @Binding var tab: AppTab

    var body: some View {
        HStack(spacing: 2) {
            ForEach(AppTab.allCases, id: \.self) { t in
                let isOn = t == tab
                Button {
                    tab = t
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: t.symbol).font(.system(size: 11, weight: .semibold))
                        Text(t.rawValue).font(Theme.titleFont(11)).tracking(1)
                    }
                    .foregroundStyle(isOn ? Color.black : Theme.label)
                    .padding(.horizontal, 8)
                    .frame(height: 30)
                    .background(RoundedRectangle(cornerRadius: 5).fill(isOn ? Theme.cream : Color.clear))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isOn ? .isSelected : [])
            }
        }
        .padding(2)
        .background(RoundedRectangle(cornerRadius: 7).fill(Theme.button))
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
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                    .frame(minWidth: 64, maxWidth: 120)
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
