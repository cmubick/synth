import SwiftUI

/// The DRUMS tab: pads on the left, sound and pattern controls on the
/// right, and the 16 step buttons along the bottom.
struct DrumScreen: View {
    var body: some View {
        GeometryReader { geo in
            let stepsHeight = min(92, max(64, geo.size.height * 0.27))
            VStack(spacing: 8) {
                HStack(spacing: 8) {
                    PadGrid()
                        .frame(width: geo.size.width * 0.4)
                    VStack(spacing: 8) {
                        TrackControls()
                        PatternBar()
                        SongStrip()
                    }
                }
                StepRow()
                    .frame(height: stepsHeight)
            }
            .environment(\.knobSize, knobSize(width: geo.size.width * 0.6 - 8,
                                              height: geo.size.height - stepsHeight))
        }
    }
}

/// Six knobs (each knob + 16 pt of label room) plus the drum name and
/// padding must fit the right-hand column; they also scale with height.
private func knobSize(width: CGFloat, height: CGFloat) -> CGFloat {
    let byWidth = (width - 85) / 6 - 16
    return min(42, max(28, min(byWidth, height * 0.16)))
}

// MARK: - Pads

struct PadGrid: View {
    private let columns = 5

    var body: some View {
        let rows = DrumMachine.trackCount / columns
        Grid(horizontalSpacing: 6, verticalSpacing: 6) {
            ForEach(0..<rows, id: \.self) { row in
                GridRow {
                    ForEach(0..<columns, id: \.self) { col in
                        DrumPad(track: row * columns + col)
                    }
                }
            }
        }
    }
}

struct DrumPad: View {
    let track: Int

    @Environment(DrumMachine.self) private var drums
    @GestureState private var isPressed = false
    @State private var glow: Double = 0

    var body: some View {
        let info = DrumMachine.tracks[track]
        let selected = drums.selectedTrack == track

        ZStack {
            RoundedRectangle(cornerRadius: 10)
                .fill(LinearGradient(colors: [Color(white: 0.26), Color(white: 0.16)],
                                     startPoint: .top, endPoint: .bottom))
            RoundedRectangle(cornerRadius: 10)
                .fill(Theme.accent)
                .opacity(glow * 0.85)
            RoundedRectangle(cornerRadius: 10)
                .stroke(selected ? Theme.accent : Color.white.opacity(0.08), lineWidth: selected ? 2 : 1)

            VStack(spacing: 2) {
                Text(info.shortName)
                    .font(Theme.titleFont(20))
                    .foregroundStyle(glow > 0.4 ? Color.black : Theme.cream)
                Text(info.name)
                    .font(Theme.labelFont(9))
                    .foregroundStyle(glow > 0.4 ? Color.black.opacity(0.7) : Theme.label)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .padding(.horizontal, 2)

            // Dot: this drum has steps in the pattern being edited.
            if drums.hasSteps(track: track) {
                Circle()
                    .fill(Theme.accent)
                    .frame(width: 5, height: 5)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                    .padding(7)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .scaleEffect(isPressed ? 0.95 : 1)
        .animation(.easeOut(duration: 0.08), value: isPressed)
        .contentShape(RoundedRectangle(cornerRadius: 10))
        // Fires on touch-down (not release), and each pad tracks its own finger.
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($isPressed) { _, pressed, _ in
                    if !pressed {
                        pressed = true
                        drums.hitPad(track)
                    }
                }
        )
        .onChange(of: drums.hits[track]) {
            glow = 1
            DispatchQueue.main.async {
                withAnimation(.easeOut(duration: 0.25)) { glow = 0 }
            }
        }
        .accessibilityElement()
        .accessibilityLabel(info.name)
        .accessibilityAddTraits([.isButton, selected ? .isSelected : []])
        .accessibilityAction { drums.hitPad(track) }
    }
}

// MARK: - Sound controls for the selected drum

struct TrackControls: View {
    @Environment(DrumMachine.self) private var drums

    var body: some View {
        let track = drums.selectedTrack
        let info = DrumMachine.tracks[track]

        HStack(alignment: .top, spacing: 2) {
            Text(info.name.uppercased())
                .font(Theme.titleFont(12))
                .tracking(1)
                .foregroundStyle(Theme.accent)
                .lineLimit(2)
                .minimumScaleFactor(0.6)
                .frame(width: 52, alignment: .leading)
                .padding(.top, 4)

            ForEach(0..<DrumMachine.soundParams.count, id: \.self) { p in
                let param = DrumMachine.soundParams[p]
                RotaryKnob(
                    label: info.paramNames[p],
                    value: drums.soundValue(track: track, param: p),
                    range: 0...1,
                    defaultValue: info.defaults[p],
                    bipolar: param == SE_DRUM_TUNE,
                    format: { formatDrum(param, $0) },
                    onChange: { drums.setSound(track: track, param: p, value: $0) }
                )
            }

            Rectangle().fill(Theme.moduleEdge).frame(width: 1).padding(.vertical, 4)

            RotaryKnob(
                label: "Swing", value: drums.swing, range: 0...1, defaultValue: 0,
                format: { "\(Int((50 + 25 * $0).rounded()))%" },
                onChange: drums.setSwing
            )
            RotaryKnob(
                label: "Kit Vol", value: drums.volume, range: 0...1, defaultValue: 0.8,
                format: { "\(Int(($0 * 100).rounded()))%" },
                onChange: drums.setVolume
            )
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 10).fill(Theme.module))
    }

    private func formatDrum(_ param: se_drum_param, _ value: Float) -> String {
        var buffer = [CChar](repeating: 0, count: 16)
        se_drum_param_format(param, value, &buffer, Int32(buffer.count))
        return buffer.withUnsafeBufferPointer { String(cString: $0.baseAddress!) }
    }
}

// MARK: - Patterns A–H

struct PatternBar: View {
    @Environment(DrumMachine.self) private var drums

    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<DrumMachine.patternCount, id: \.self) { p in
                PatternButton(pattern: p)
            }

            Toggle(isOn: Binding(get: { drums.songMode }, set: drums.setSongMode)) {
                Text("SONG")
                    .font(Theme.titleFont(11))
                    .tracking(1)
            }
            .toggleStyle(LitButtonStyle())
            .fixedSize()
            .accessibilityHint("Play patterns in order. Tap patterns to add them to the song.")

            PatternMenu()
        }
    }
}

struct PatternButton: View {
    let pattern: Int
    @Environment(DrumMachine.self) private var drums

    var body: some View {
        let editing = drums.editPattern == pattern
        let playing = drums.isPlaying && drums.playingPattern == pattern
        let hasSteps = drums.patternHasSteps(pattern)

        Button {
            drums.selectPattern(pattern)
        } label: {
            ZStack(alignment: .topTrailing) {
                Text(DrumMachine.patternNames[pattern])
                    .font(Theme.titleFont(14))
                    .foregroundStyle(editing ? Color.black : (hasSteps ? Theme.cream : Theme.label.opacity(0.4)))
                    .frame(maxWidth: .infinity, minHeight: 34)
                    .background(RoundedRectangle(cornerRadius: 6).fill(editing ? Theme.accent : Theme.button))
                Circle()
                    .fill(playing ? Color.green : Color.clear)
                    .frame(width: 6, height: 6)
                    .shadow(color: .green, radius: playing ? 3 : 0)
                    .padding(4)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Pattern \(DrumMachine.patternNames[pattern])")
        .accessibilityAddTraits(editing ? .isSelected : [])
    }
}

struct PatternMenu: View {
    @Environment(DrumMachine.self) private var drums

    var body: some View {
        let name = DrumMachine.patternNames[drums.editPattern]
        Menu {
            Button("Clear \(DrumMachine.tracks[drums.selectedTrack].name) in \(name)", systemImage: "eraser") {
                drums.clearTrack()
            }
            Button("Clear Pattern \(name)", systemImage: "trash", role: .destructive) {
                drums.clearPattern()
            }
            Menu("Copy \(name) To…", systemImage: "doc.on.doc") {
                ForEach(0..<DrumMachine.patternCount, id: \.self) { p in
                    if p != drums.editPattern {
                        Button("Pattern \(DrumMachine.patternNames[p])") { drums.copyPattern(to: p) }
                    }
                }
            }
            Picker("Length", selection: Binding(get: { drums.lengths[drums.editPattern] },
                                                set: { drums.setLength($0) })) {
                ForEach([16, 12, 8, 6, 4, 3], id: \.self) { Text("\($0) steps").tag($0) }
            }
            .pickerStyle(.menu)
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Theme.cream)
                .frame(width: 34, height: 34)
                .background(RoundedRectangle(cornerRadius: 6).fill(Theme.button))
        }
        .accessibilityLabel("Pattern options")
    }
}

/// The song: the order patterns play in when SONG is on.
struct SongStrip: View {
    @Environment(DrumMachine.self) private var drums

    var body: some View {
        HStack(spacing: 6) {
            Text("SONG")
                .font(Theme.titleFont(9))
                .tracking(1)
                .foregroundStyle(drums.songMode ? Theme.accent : Theme.label.opacity(0.5))

            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 3) {
                        if drums.song.isEmpty {
                            Text(drums.songMode ? "Tap patterns above to add them in order" : "Turn on SONG to chain patterns")
                                .font(Theme.labelFont(10))
                                .foregroundStyle(Theme.label.opacity(0.6))
                        }
                        ForEach(Array(drums.song.enumerated()), id: \.offset) { i, p in
                            let current = drums.songMode && drums.isPlaying && drums.songPosition == i
                            Text(DrumMachine.patternNames[p])
                                .font(Theme.titleFont(11))
                                .foregroundStyle(current ? Color.black : Theme.cream.opacity(drums.songMode ? 1 : 0.4))
                                .frame(width: 20, height: 22)
                                .background(RoundedRectangle(cornerRadius: 4)
                                    .fill(current ? Theme.accent : Theme.button))
                                .id(i)
                        }
                    }
                }
                .onChange(of: drums.songPosition) { _, pos in
                    withAnimation { proxy.scrollTo(pos, anchor: .center) }
                }
            }

            if drums.songMode && !drums.song.isEmpty {
                Button { drums.removeLastFromSong() } label: {
                    Image(systemName: "delete.left").font(.system(size: 12, weight: .semibold))
                }
                .accessibilityLabel("Remove last pattern from song")
                Button("Clear") { drums.clearSong() }
                    .font(Theme.labelFont(11))
            }
        }
        .foregroundStyle(Theme.cream)
        .padding(.horizontal, 8)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 8).fill(Color.black.opacity(0.25)))
    }
}

// MARK: - The 16 steps

struct StepRow: View {
    @Environment(DrumMachine.self) private var drums

    var body: some View {
        @Bindable var drums = drums
        HStack(spacing: 6) {
            VStack(spacing: 5) {
                Toggle(isOn: $drums.accentMode) {
                    Text("ACCENT").font(Theme.titleFont(9)).tracking(0.5)
                }
                .toggleStyle(LitButtonStyle())
                .accessibilityHint("When lit, tapping steps and pads adds accents")
                Toggle(isOn: $drums.recordMode) {
                    Text("● REC").font(Theme.titleFont(9)).tracking(0.5)
                }
                .toggleStyle(LitButtonStyle(color: .red))
                .accessibilityHint("When lit and playing, pads are recorded into the pattern")
            }
            .frame(width: 62)

            HStack(spacing: 3) {
                ForEach(0..<DrumMachine.stepCount, id: \.self) { s in
                    StepButton(step: s)
                }
            }
        }
    }
}

struct StepButton: View {
    let step: Int
    @Environment(DrumMachine.self) private var drums

    // The classic four colors, one per beat.
    static let beatColors: [Color] = [
        Color(red: 0.93, green: 0.27, blue: 0.20),
        Color(red: 0.98, green: 0.55, blue: 0.20),
        Color(red: 0.98, green: 0.82, blue: 0.30),
        Color(red: 0.94, green: 0.90, blue: 0.83),
    ]

    var body: some View {
        let value = drums.step(track: drums.selectedTrack, step: step)
        let color = Self.beatColors[step / 4]
        let inPattern = step < drums.lengths[drums.editPattern]
        let isPlayhead = drums.isPlaying && drums.currentStep == step && drums.playingPattern == drums.editPattern

        Button {
            drums.toggleStep(step)
        } label: {
            VStack(spacing: 0) {
                ZStack {
                    RoundedRectangle(cornerRadius: 5)
                        .fill(value > 0 ? color : Color(white: 0.2))
                        .shadow(color: value > 0 ? color.opacity(0.6) : .clear, radius: 4)
                    if value == 2 {
                        Image(systemName: "chevron.up")
                            .font(.system(size: 11, weight: .black))
                            .foregroundStyle(Color.black.opacity(0.7))
                    }
                }
                Rectangle()
                    .fill(color.opacity(value > 0 ? 0 : 0.8))
                    .frame(height: 4)
                    .padding(.horizontal, 4)
                    .padding(.top, 3)
                Text("\(step + 1)")
                    .font(Theme.labelFont(9))
                    .foregroundStyle(Theme.label.opacity(0.6))
                    .padding(.top, 2)
            }
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(Color.white, lineWidth: isPlayhead ? 2 : 0)
                    .padding(-2)
            )
            .opacity(inPattern ? 1 : 0.25)
        }
        .buttonStyle(.plain)
        .disabled(!inPattern)
        .accessibilityLabel("Step \(step + 1)")
        .accessibilityValue(value == 0 ? "off" : (value == 2 ? "accent" : "on"))
    }
}

/// A button that lights up when on.
struct LitButtonStyle: ToggleStyle {
    var color: Color = Theme.accent

    func makeBody(configuration: Configuration) -> some View {
        Button {
            configuration.isOn.toggle()
        } label: {
            configuration.label
                .foregroundStyle(configuration.isOn ? Color.black : Theme.label)
                .frame(maxWidth: .infinity, minHeight: 30)
                .padding(.horizontal, 6)
                .background(
                    RoundedRectangle(cornerRadius: 6)
                        .fill(configuration.isOn ? color : Theme.button)
                        .shadow(color: configuration.isOn ? color.opacity(0.6) : .clear, radius: 4)
                )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(configuration.isOn ? .isSelected : [])
    }
}
