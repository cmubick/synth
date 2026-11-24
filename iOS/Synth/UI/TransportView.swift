import SwiftUI

/// Play/stop, tempo and tap tempo. Lives in the top bar on every tab so
/// the beat keeps going while you play the synth.
struct TransportView: View {
    @Environment(DrumMachine.self) private var drums

    var body: some View {
        HStack(spacing: 6) {
            Button {
                Haptics.thump()
                drums.togglePlay()
            } label: {
                Image(systemName: drums.isPlaying ? "stop.fill" : "play.fill")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(drums.isPlaying ? Color.black : Theme.cream)
                    .frame(width: 38, height: 34)
                    .background(RoundedRectangle(cornerRadius: 7)
                        .fill(drums.isPlaying ? Color.green : Theme.button))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(drums.isPlaying ? "Stop" : "Play")

            TempoDisplay()

            Button {
                drums.tapTempo()
            } label: {
                Text("TAP")
                    .font(Theme.titleFont(10))
                    .foregroundStyle(Theme.label)
                    .frame(width: 34, height: 34)
                    .background(RoundedRectangle(cornerRadius: 7).fill(Theme.button))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Tap tempo")
        }
    }
}

/// Tempo readout: drag up/down to change it, double-tap for 120.
struct TempoDisplay: View {
    @Environment(DrumMachine.self) private var drums
    @GestureState private var dragStart: Float?

    var body: some View {
        // A beat light that blinks on every quarter note.
        let onBeat = drums.isPlaying && drums.currentStep >= 0 && drums.currentStep % 4 == 0

        HStack(spacing: 5) {
            Circle()
                .fill(onBeat ? Theme.accent : Theme.track)
                .frame(width: 6, height: 6)
                .shadow(color: Theme.accent, radius: onBeat ? 3 : 0)
            VStack(spacing: -2) {
                Text("\(Int(drums.tempo))")
                    .font(.system(size: 17, weight: .heavy, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(dragStart != nil ? Theme.accent : Theme.cream)
                Text("BPM")
                    .font(Theme.titleFont(8))
                    .foregroundStyle(Theme.label)
            }
        }
        .frame(width: 58, height: 34)
        .background(RoundedRectangle(cornerRadius: 7).fill(Color.black.opacity(0.5)))
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 1)
                .updating($dragStart) { drag, start, _ in
                    if start == nil { start = drums.tempo }
                    drums.setTempo(start! - Float(drag.translation.height) / 3)
                }
        )
        .onTapGesture(count: 2) { drums.setTempo(120) }
        .accessibilityElement()
        .accessibilityLabel("Tempo")
        .accessibilityValue("\(Int(drums.tempo)) BPM")
        .accessibilityAdjustableAction { direction in
            drums.setTempo(drums.tempo + (direction == .increment ? 1 : -1))
        }
    }
}

struct BeatPicker: View {
    @Environment(DrumMachine.self) private var drums
    @Environment(\.openMore) private var openMore

    var body: some View {
        Menu {
            Section("Premade beats") {
                ForEach(Beat.factory.indices, id: \.self) { i in
                    let beat = Beat.factory[i]
                    if FullVersion.isLocked(beat) {
                        // Locked beats stay in the list, so players can see what the full version adds.
                        Button(beat.name, systemImage: "lock.fill") { openMore(.fullVersion) }
                    } else {
                        Button(beat.name) { drums.load(beat) }
                    }
                }
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "music.note.list")
                    .font(.system(size: 11, weight: .semibold))
                Text(drums.beatName)
                    .font(Theme.titleFont(13))
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                Image(systemName: "chevron.down")
                    .font(.system(size: 9, weight: .bold))
            }
            .foregroundStyle(Theme.accent)
            .padding(.horizontal, 10)
            .frame(minWidth: 96, maxWidth: 190, minHeight: 34)
            .background(RoundedRectangle(cornerRadius: 7).fill(Color.black.opacity(0.5)))
            .overlay(RoundedRectangle(cornerRadius: 7).stroke(Theme.moduleEdge))
        }
        .accessibilityLabel("Beat: \(drums.beatName)")
    }
}
