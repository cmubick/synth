import SwiftUI

@main
struct SynthApp: App {
    @State private var patch: SynthPatch
    @State private var drums: DrumMachine

    init() {
        let audio = AudioEngine()
        audio.start()
        Store.shared.start()
        _patch = State(initialValue: SynthPatch(audio: audio))
        _drums = State(initialValue: DrumMachine(audio: audio))
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(patch)
                .environment(drums)
                .preferredColorScheme(.dark)
        }
    }
}
