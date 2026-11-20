import Observation
import SwiftUI

/// The current sound: every knob's value. Changing a value updates the
/// screen and the engine together.
@Observable
final class SynthPatch {
    private(set) var values: [Float]
    private(set) var presetIndex: Int = 0

    @ObservationIgnored let audio: AudioEngine

    var presetName: String { Preset.factory[presetIndex].name }

    init(audio: AudioEngine) {
        self.audio = audio
        values = ParamInfo.all.map(\.defaultValue)
        load(presetAt: 0)
    }

    subscript(_ id: se_param) -> Float {
        get { values[Int(id.rawValue)] }
        set {
            let info = ParamInfo.of(id)
            var v = Swift.min(Swift.max(newValue, info.min), info.max)
            if info.isDiscrete { v = v.rounded() }
            guard v != values[Int(id.rawValue)] else { return }
            values[Int(id.rawValue)] = v
            audio.setParam(id, v)
        }
    }

    func load(presetAt index: Int) {
        let count = Preset.factory.count
        presetIndex = ((index % count) + count) % count
        let preset = Preset.factory[presetIndex]

        var newValues = ParamInfo.all.map(\.defaultValue)
        for (key, value) in preset.values {
            if let i = ParamInfo.index(forKey: key) {
                newValues[i] = value
            } else {
                assertionFailure("Preset \(preset.name) uses unknown key \(key)")
            }
        }
        // Keep the player's volume when switching sounds.
        let volume = Int(SE_MASTER_VOLUME.rawValue)
        newValues[volume] = values[volume]

        values = newValues
        for (i, v) in values.enumerated() {
            audio.setParam(ParamInfo.all[i].id, v)
        }
    }
}
