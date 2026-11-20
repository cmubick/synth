import SwiftUI

@main
struct SynthApp: App {
    @State private var patch: SynthPatch

    init() {
        let audio = AudioEngine()
        audio.start()
        _patch = State(initialValue: SynthPatch(audio: audio))
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(patch)
                .preferredColorScheme(.dark)
        }
    }
}
