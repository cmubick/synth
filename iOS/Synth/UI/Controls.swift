import SwiftUI

/// A vertical slider, like the envelope faders on a hardware synth.
struct Fader: View {
    let id: se_param
    let label: String

    @Environment(SynthPatch.self) private var patch
    @Environment(\.faderHeight) private var height
    @GestureState private var dragStart: Float?

    var body: some View {
        let info = ParamInfo.of(id)
        let value = patch[id]
        let norm = CGFloat(info.normalized(value))
        let capHeight: CGFloat = 12
        let isDragging = dragStart != nil

        VStack(spacing: 3) {
            ZStack(alignment: .bottom) {
                Capsule().fill(Theme.track).frame(width: 6)
                Capsule().fill(Theme.accent.opacity(0.85))
                    .frame(width: 6, height: max(6, norm * height))
                RoundedRectangle(cornerRadius: 3)
                    .fill(LinearGradient(colors: [Color(white: 0.85), Color(white: 0.55)],
                                         startPoint: .top, endPoint: .bottom))
                    .overlay(Rectangle().fill(Color.black.opacity(0.6)).frame(height: 1.5))
                    .frame(width: 26, height: capHeight)
                    .shadow(color: .black.opacity(0.5), radius: 2, y: 1)
                    .offset(y: -norm * (height - capHeight))
            }
            .frame(width: 30, height: height)
            .contentShape(Rectangle())
            .highPriorityGesture(
                DragGesture(minimumDistance: 1)
                    .updating($dragStart) { drag, start, _ in
                        if start == nil { start = patch[id] }
                        let delta = Float(-drag.translation.height / max(height, 60)) * (info.max - info.min)
                        patch[id] = start! + delta
                    }
            )
            .onTapGesture(count: 2) { patch[id] = info.defaultValue }

            Text(isDragging ? info.format(value) : label)
                .font(Theme.labelFont())
                .foregroundStyle(isDragging ? Theme.accent : Theme.label)
                .lineLimit(1)
                .fixedSize()
                .frame(width: 30)
        }
        .accessibilityElement()
        .accessibilityLabel(info.name)
        .accessibilityValue(info.format(value))
        .accessibilityAdjustableAction { direction in
            patch[id] = value + (direction == .increment ? 0.05 : -0.05)
        }
    }
}

/// Row of lit buttons for picking a waveform.
struct WaveSelector: View {
    let id: se_param
    let title: String
    var shapes: [WaveIcon.Kind] = [.saw, .square, .triangle, .sine]

    @Environment(SynthPatch.self) private var patch

    var body: some View {
        let selected = Int(patch[id])
        VStack(spacing: 4) {
            Text(title)
                .font(Theme.labelFont())
                .foregroundStyle(Theme.label)
            HStack(spacing: 3) {
                ForEach(shapes.indices, id: \.self) { i in
                    let isOn = i == selected
                    Button {
                        patch[id] = Float(i)
                    } label: {
                        WaveIcon(kind: shapes[i])
                            .stroke(isOn ? Color.black : Theme.cream,
                                    style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                            .frame(width: 18, height: 11)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 7)
                            .background(
                                RoundedRectangle(cornerRadius: 5)
                                    .fill(isOn ? Theme.accent : Theme.button)
                                    .shadow(color: isOn ? Theme.accent.opacity(0.6) : .clear, radius: 4)
                            )
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(ParamInfo.of(id).format(Float(i)))
                    .accessibilityAddTraits(isOn ? .isSelected : [])
                }
            }
        }
    }
}

/// Two-position switch, e.g. POLY / MONO.
struct SegmentSwitch: View {
    let id: se_param
    let labels: [String]

    @Environment(SynthPatch.self) private var patch

    var body: some View {
        let selected = Int(patch[id])
        HStack(spacing: 2) {
            ForEach(labels.indices, id: \.self) { i in
                let isOn = i == selected
                Button {
                    patch[id] = Float(i)
                } label: {
                    Text(labels[i])
                        .font(Theme.titleFont(11))
                        .tracking(1)
                        .foregroundStyle(isOn ? Color.black : Theme.label)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 7)
                        .background(RoundedRectangle(cornerRadius: 5).fill(isOn ? Theme.accent : Color.clear))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isOn ? .isSelected : [])
            }
        }
        .padding(2)
        .background(RoundedRectangle(cornerRadius: 7).fill(Theme.button))
    }
}

/// Tiny drawings of each waveform for the selector buttons.
struct WaveIcon: Shape {
    enum Kind { case saw, square, triangle, sine, random }
    let kind: Kind

    func path(in r: CGRect) -> Path {
        var p = Path()
        let top = r.minY, bottom = r.maxY, mid = r.midY
        let x0 = r.minX, x1 = r.maxX, w = r.width
        switch kind {
        case .saw:
            p.move(to: CGPoint(x: x0, y: bottom))
            p.addLine(to: CGPoint(x: x0 + w / 2, y: top))
            p.addLine(to: CGPoint(x: x0 + w / 2, y: bottom))
            p.addLine(to: CGPoint(x: x1, y: top))
        case .square:
            p.move(to: CGPoint(x: x0, y: bottom))
            p.addLine(to: CGPoint(x: x0, y: top))
            p.addLine(to: CGPoint(x: x0 + w / 2, y: top))
            p.addLine(to: CGPoint(x: x0 + w / 2, y: bottom))
            p.addLine(to: CGPoint(x: x1, y: bottom))
            p.addLine(to: CGPoint(x: x1, y: top))
        case .triangle:
            p.move(to: CGPoint(x: x0, y: bottom))
            p.addLine(to: CGPoint(x: x0 + w / 4, y: top))
            p.addLine(to: CGPoint(x: x0 + 3 * w / 4, y: bottom))
            p.addLine(to: CGPoint(x: x1, y: top))
        case .sine:
            p.move(to: CGPoint(x: x0, y: mid))
            for i in 1...24 {
                let t = CGFloat(i) / 24
                p.addLine(to: CGPoint(x: x0 + t * w, y: mid - sin(t * 2 * .pi) * r.height / 2))
            }
        case .random:
            let levels: [CGFloat] = [0.7, 0.1, 0.9, 0.4]
            p.move(to: CGPoint(x: x0, y: top + levels[0] * r.height))
            for (i, level) in levels.enumerated() {
                let y = top + level * r.height
                p.addLine(to: CGPoint(x: x0 + CGFloat(i) * w / 4, y: y))
                p.addLine(to: CGPoint(x: x0 + CGFloat(i + 1) * w / 4, y: y))
            }
        }
        return p
    }
}

/// A labeled section of the front panel.
struct Module<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    init(_ title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(spacing: 6) {
            Text(title)
                .font(Theme.titleFont())
                .tracking(1.6)
                .foregroundStyle(Theme.cream.opacity(0.6))
            content
                .frame(maxHeight: .infinity)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .frame(maxHeight: .infinity, alignment: .top)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Theme.module)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.moduleEdge, lineWidth: 1))
        )
    }
}
