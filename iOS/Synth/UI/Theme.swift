import SwiftUI

/// Look of the instrument: dark panel, cream markings, orange highlights,
/// walnut side panels.
enum Theme {
    static let panel = Color(red: 0.11, green: 0.105, blue: 0.10)
    static let module = Color(red: 0.165, green: 0.155, blue: 0.145)
    static let moduleEdge = Color.white.opacity(0.06)
    static let accent = Color(red: 1.0, green: 0.54, blue: 0.24)
    static let cream = Color(red: 0.94, green: 0.90, blue: 0.83)
    static let label = Color(red: 0.94, green: 0.90, blue: 0.83).opacity(0.75)
    static let track = Color.black.opacity(0.45)
    static let button = Color(red: 0.23, green: 0.22, blue: 0.21)

    static let knobGradient = LinearGradient(
        colors: [Color(white: 0.30), Color(white: 0.12)],
        startPoint: .top, endPoint: .bottom
    )

    static let wood = LinearGradient(
        colors: [
            Color(red: 0.36, green: 0.20, blue: 0.11),
            Color(red: 0.47, green: 0.28, blue: 0.15),
            Color(red: 0.33, green: 0.18, blue: 0.10),
            Color(red: 0.43, green: 0.25, blue: 0.13),
        ],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )

    static func labelFont(_ size: CGFloat = 10) -> Font {
        .system(size: size, weight: .semibold, design: .rounded)
    }

    static func titleFont(_ size: CGFloat = 10) -> Font {
        .system(size: size, weight: .heavy, design: .rounded)
    }
}

// Control sizes adapt to the screen; set once at the top of the panel.
private struct KnobSizeKey: EnvironmentKey {
    static let defaultValue: CGFloat = 44
}

private struct FaderHeightKey: EnvironmentKey {
    static let defaultValue: CGFloat = 100
}

extension EnvironmentValues {
    var knobSize: CGFloat {
        get { self[KnobSizeKey.self] }
        set { self[KnobSizeKey.self] = newValue }
    }

    var faderHeight: CGFloat {
        get { self[FaderHeightKey.self] }
        set { self[FaderHeightKey.self] = newValue }
    }
}
