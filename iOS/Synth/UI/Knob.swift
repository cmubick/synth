import SwiftUI

/// A rotary knob. Drag up/down to turn it, double-tap to reset.
/// While turning, the label shows the value (e.g. "1.2 kHz").
struct Knob: View {
    let id: se_param
    var label: String? = nil
    var scale: CGFloat = 1

    @Environment(SynthPatch.self) private var patch
    @Environment(\.knobSize) private var baseSize
    // Resets automatically when the drag ends *or* is cancelled (e.g. by scrolling).
    @GestureState private var dragStart: Float?

    var body: some View {
        let info = ParamInfo.of(id)
        let value = patch[id]
        let size = baseSize * scale
        let isDragging = dragStart != nil

        VStack(spacing: 3) {
            KnobFace(norm: CGFloat(info.normalized(value)), bipolar: info.isBipolar, active: isDragging)
                .frame(width: size, height: size)
                .contentShape(Circle())
                .highPriorityGesture(
                    DragGesture(minimumDistance: 1)
                        .updating($dragStart) { drag, start, _ in
                            if start == nil { start = patch[id] }
                            // A full sweep takes ~3 knob-heights of finger travel (at least 150 pt).
                            let travel = Float(max(150, size * 3.2))
                            let delta = Float(-drag.translation.height) / travel * (info.max - info.min)
                            patch[id] = start! + delta
                        }
                )
                .onTapGesture(count: 2) { patch[id] = info.defaultValue }

            Text(isDragging ? info.format(value) : (label ?? info.name))
                .font(Theme.labelFont())
                .foregroundStyle(isDragging ? Theme.accent : Theme.label)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(width: size + 16)
        }
        .accessibilityElement()
        .accessibilityLabel(label ?? info.name)
        .accessibilityValue(info.format(value))
        .accessibilityAdjustableAction { direction in
            let step = info.isDiscrete ? 1 : (info.max - info.min) / 20
            patch[id] = value + (direction == .increment ? step : -step)
        }
    }
}

struct KnobFace: View {
    let norm: CGFloat
    let bipolar: Bool
    var active: Bool = false

    var body: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            let ring = max(2.5, size * 0.065)
            let arcStart = bipolar ? 0.375 : 0
            let arcEnd = 0.75 * norm

            ZStack {
                Circle()
                    .trim(from: 0, to: 0.75)
                    .rotation(.degrees(135))
                    .stroke(Theme.track, style: StrokeStyle(lineWidth: ring, lineCap: .round))

                Circle()
                    .trim(from: min(arcStart, arcEnd), to: max(arcStart, arcEnd))
                    .rotation(.degrees(135))
                    .stroke(Theme.accent, style: StrokeStyle(lineWidth: ring, lineCap: .round))
                    .shadow(color: Theme.accent.opacity(active ? 0.7 : 0.3), radius: active ? 4 : 2)

                Circle()
                    .fill(Theme.knobGradient)
                    .overlay(Circle().stroke(Color.white.opacity(0.12), lineWidth: 1))
                    .padding(size * 0.17)
                    .shadow(color: .black.opacity(0.5), radius: 3, y: 2)

                Capsule()
                    .fill(Theme.cream)
                    .frame(width: max(2, size * 0.055), height: size * 0.22)
                    .offset(y: -size * 0.2)
                    .rotationEffect(.degrees(-135 + 270 * Double(norm)))
            }
            .padding(ring / 2)
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }
}
