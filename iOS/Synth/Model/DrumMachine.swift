import Observation
import QuartzCore
import SwiftUI

/// The drum machine as the screen sees it: patterns, kit settings, tempo,
/// transport and what's playing right now.
///
/// Pattern steps are kept here *and* in the engine. The screen reads this
/// copy; every edit is written through to the engine, which does the
/// actual timing on the audio thread.
@Observable
final class DrumMachine {
    static let trackCount = Int(SE_DRUM_TRACKS)
    static let stepCount = Int(SE_SEQ_STEPS)
    static let patternCount = Int(SE_SEQ_PATTERNS)
    static let maxSongLength = Int(SE_SEQ_MAX_CHAIN)
    static let patternNames = ["A", "B", "C", "D", "E", "F", "G", "H"]
    static let soundParams: [se_drum_param] = [SE_DRUM_LEVEL, SE_DRUM_TUNE, SE_DRUM_DECAY, SE_DRUM_TONE]

    struct TrackInfo {
        let name: String
        let shortName: String
        let paramNames: [String]
        let defaults: [Float]
    }

    static let tracks: [TrackInfo] = (0..<trackCount).map { t in
        let i = Int32(t)
        return TrackInfo(
            name: String(cString: se_drum_name(i)),
            shortName: String(cString: se_drum_short_name(i)),
            paramNames: soundParams.map { String(cString: se_drum_param_name(i, $0)) },
            defaults: soundParams.map { se_drum_param_default(i, $0) }
        )
    }

    static func trackIndex(shortName: String) -> Int? {
        tracks.firstIndex { $0.shortName == shortName }
    }

    @ObservationIgnored let audio: AudioEngine
    private var core: OpaquePointer { audio.core }

    // Patterns: steps[pattern][track][step] = 0 off, 1 on, 2 accent
    private(set) var steps: [[[Int]]]
    private(set) var lengths: [Int]
    private(set) var sound: [[Float]] // sound[track][param]

    // Editing state
    var selectedTrack = 0
    var accentMode = false
    var recordMode = false
    private(set) var editPattern = 0
    private(set) var songMode = false
    private(set) var song: [Int] = []
    private(set) var beatName = ""

    // Groove & transport
    private(set) var tempo: Float = 120
    private(set) var swing: Float = 0
    private(set) var volume: Float = 0.8
    private(set) var isPlaying = false

    // Live readouts from the engine
    private(set) var currentStep = -1
    private(set) var playingPattern = 0
    private(set) var songPosition = 0
    private(set) var hits: [UInt32]

    @ObservationIgnored private var lastEditCount: UInt32 = 0
    @ObservationIgnored private var displayLink: CADisplayLink?
    @ObservationIgnored private var tapTimes: [CFTimeInterval] = []

    init(audio: AudioEngine) {
        self.audio = audio
        let empty = [[Int]](repeating: [Int](repeating: 0, count: Self.stepCount), count: Self.trackCount)
        steps = [[[Int]]](repeating: empty, count: Self.patternCount)
        lengths = [Int](repeating: Self.stepCount, count: Self.patternCount)
        sound = Self.tracks.map(\.defaults)
        hits = [UInt32](repeating: 0, count: Self.trackCount)
        load(Beat.factory[0])
        startPolling()
    }

    deinit {
        displayLink?.invalidate()
    }

    // MARK: Steps

    func step(track: Int, step: Int) -> Int {
        steps[editPattern][track][step]
    }

    func hasSteps(track: Int) -> Bool {
        steps[editPattern][track].contains { $0 > 0 }
    }

    func patternHasSteps(_ pattern: Int) -> Bool {
        steps[pattern].contains { $0.contains { $0 > 0 } }
    }

    /// Tap a step: on/off, or accent on/off when ACCENT is lit.
    func toggleStep(_ step: Int) {
        let current = steps[editPattern][selectedTrack][step]
        let next: Int
        if accentMode {
            next = current == 2 ? 1 : 2
        } else {
            next = current == 0 ? 1 : 0
        }
        setStep(pattern: editPattern, track: selectedTrack, step: step, value: next)
    }

    private func setStep(pattern: Int, track: Int, step: Int, value: Int) {
        steps[pattern][track][step] = value
        se_seq_set_step(core, Int32(pattern), Int32(track), Int32(step), Int32(value))
    }

    func clearTrack() {
        for s in 0..<Self.stepCount {
            setStep(pattern: editPattern, track: selectedTrack, step: s, value: 0)
        }
    }

    func clearPattern() {
        for t in 0..<Self.trackCount {
            for s in 0..<Self.stepCount {
                setStep(pattern: editPattern, track: t, step: s, value: 0)
            }
        }
    }

    func copyPattern(to target: Int) {
        guard target != editPattern else { return }
        for t in 0..<Self.trackCount {
            for s in 0..<Self.stepCount {
                setStep(pattern: target, track: t, step: s, value: steps[editPattern][t][s])
            }
        }
        setLength(lengths[editPattern], pattern: target)
    }

    func setLength(_ length: Int, pattern: Int? = nil) {
        let p = pattern ?? editPattern
        lengths[p] = min(max(length, 1), Self.stepCount)
        se_seq_set_length(core, Int32(p), Int32(lengths[p]))
    }

    // MARK: Patterns & song

    /// Choose a pattern to edit and play. In song mode it's also added to the song.
    func selectPattern(_ pattern: Int) {
        editPattern = pattern
        if songMode && song.count < Self.maxSongLength {
            song.append(pattern)
        }
        pushChain()
    }

    func setSongMode(_ on: Bool) {
        songMode = on
        pushChain()
    }

    func removeLastFromSong() {
        guard !song.isEmpty else { return }
        song.removeLast()
        pushChain()
    }

    func clearSong() {
        song.removeAll()
        pushChain()
    }

    private func pushChain() {
        let chain = (songMode && !song.isEmpty ? song : [editPattern]).map { Int32($0) }
        chain.withUnsafeBufferPointer { buffer in
            se_seq_set_chain(core, buffer.baseAddress, Int32(buffer.count))
        }
    }

    // MARK: Sound

    func soundValue(track: Int, param: Int) -> Float {
        sound[track][param]
    }

    func setSound(track: Int, param: Int, value: Float) {
        sound[track][param] = value
        se_drum_set_param(core, Int32(track), Self.soundParams[param], value)
    }

    func setTempo(_ bpm: Float) {
        tempo = min(max(bpm.rounded(), 40), 240)
        se_seq_set_tempo(core, tempo)
    }

    func setSwing(_ amount: Float) {
        swing = min(max(amount, 0), 1)
        se_seq_set_swing(core, swing)
    }

    func setVolume(_ v: Float) {
        volume = min(max(v, 0), 1)
        se_drum_set_volume(core, volume)
    }

    /// Tap along to the beat; the tempo follows your taps.
    func tapTempo() {
        let now = CACurrentMediaTime()
        if let last = tapTimes.last, now - last > 2 { tapTimes.removeAll() }
        tapTimes.append(now)
        tapTimes = Array(tapTimes.suffix(5))
        guard tapTimes.count >= 2 else { return }
        let intervals = zip(tapTimes.dropFirst(), tapTimes).map { $0 - $1 }
        let average = intervals.reduce(0, +) / Double(intervals.count)
        setTempo(Float(60 / average))
    }

    // MARK: Playing

    func togglePlay() {
        isPlaying ? stop() : play()
    }

    func play() {
        se_seq_play(core)
        isPlaying = true
    }

    func stop() {
        se_seq_stop(core)
        isPlaying = false
    }

    /// A finger on a pad. Records into the pattern when REC is on and it's playing.
    func hitPad(_ track: Int) {
        selectedTrack = track
        if recordMode && isPlaying {
            se_seq_record_hit(core, Int32(track), accentMode ? 1 : 0)
        } else {
            se_drum_trigger(core, Int32(track), accentMode ? 1.0 : 0.8)
        }
    }

    // MARK: Premade beats

    func load(_ beat: Beat) {
        for p in 0..<Self.patternCount {
            let rows = p < beat.patterns.count ? beat.patterns[p] : [:]
            for t in 0..<Self.trackCount {
                let row = rows[Self.tracks[t].shortName] ?? ""
                let values = Beat.parse(row)
                for s in 0..<Self.stepCount {
                    setStep(pattern: p, track: t, step: s, value: s < values.count ? values[s] : 0)
                }
            }
            setLength(Self.stepCount, pattern: p)
        }
        for t in 0..<Self.trackCount {
            let tweaks = beat.kit[Self.tracks[t].shortName] ?? [:]
            for (i, _) in Self.soundParams.enumerated() {
                setSound(track: t, param: i, value: tweaks[i] ?? Self.tracks[t].defaults[i])
            }
        }
        setTempo(beat.tempo)
        setSwing(beat.swing)
        song = beat.song
        songMode = false
        editPattern = 0
        selectedTrack = 0
        beatName = beat.name
        pushChain()
    }

    // MARK: Following the engine

    private func startPolling() {
        let link = CADisplayLink(target: DisplayLinkTarget { [weak self] in self?.poll() },
                                 selector: #selector(DisplayLinkTarget.fire))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    private func poll() {
        // Only assign when something changed, so the screen redraws only then.
        let step = Int(se_seq_current_step(core))
        if step != currentStep { currentStep = step }
        let pattern = Int(se_seq_current_pattern(core))
        if pattern != playingPattern { playingPattern = pattern }
        let position = Int(se_seq_chain_position(core))
        if position != songPosition { songPosition = position }

        for t in 0..<Self.trackCount {
            let h = se_drum_hit_count(core, Int32(t))
            if h != hits[t] { hits[t] = h }
        }

        // Live recording changed steps inside the engine: copy them back.
        let edits = se_seq_edit_count(core)
        if edits != lastEditCount {
            lastEditCount = edits
            for p in 0..<Self.patternCount {
                for t in 0..<Self.trackCount {
                    for s in 0..<Self.stepCount {
                        let v = Int(se_seq_get_step(core, Int32(p), Int32(t), Int32(s)))
                        if steps[p][t][s] != v { steps[p][t][s] = v }
                    }
                }
            }
        }
    }
}

/// CADisplayLink needs an Objective-C target; this forwards to a closure.
private final class DisplayLinkTarget: NSObject {
    let action: () -> Void
    init(_ action: @escaping () -> Void) { self.action = action }
    @objc func fire() { action() }
}
