import AVFoundation

/// Connects the shared C++ engine to the phone's speaker / headphones.
///
/// The C++ engine does all the sound making. This class just sets up the
/// iOS audio session, asks the engine for samples on the audio thread, and
/// restarts things when the output changes (headphones plugged in, a call
/// interrupts, etc.).
final class AudioEngine {
    let core: OpaquePointer
    private let engine = AVAudioEngine()
    private var sourceNode: AVAudioSourceNode?
    private var observers: [NSObjectProtocol] = []

    init() {
        core = se_create(48_000)
    }

    deinit {
        observers.forEach(NotificationCenter.default.removeObserver)
        engine.stop()
        se_destroy(core)
    }

    // MARK: Playing

    func noteOn(_ note: Int, velocity: Float) {
        se_note_on(core, Int32(note), velocity)
    }

    func noteOff(_ note: Int) {
        se_note_off(core, Int32(note))
    }

    func allNotesOff() {
        se_all_notes_off(core)
    }

    func setParam(_ param: se_param, _ value: Float) {
        se_set_param(core, param, value)
    }

    // MARK: Lifecycle

    func start() {
        configureSession()
        connectAndStart()
        observeSystemEvents()
    }

    private func configureSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .default, options: [])
            // Match the hardware's usual rate so iOS doesn't have to convert.
            try session.setPreferredSampleRate(48_000)
            // ~5 ms buffers: keys feel instant.
            try session.setPreferredIOBufferDuration(0.005)
            try session.setActive(true)
        } catch {
            print("Audio session setup failed: \(error)")
        }
    }

    private func connectAndStart() {
        engine.stop()
        if let old = sourceNode {
            engine.detach(old)
        }

        let sampleRate = AVAudioSession.sharedInstance().sampleRate
        se_set_sample_rate(core, sampleRate)

        guard let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 2) else { return }
        let core = self.core
        let node = AVAudioSourceNode(format: format) { _, _, frameCount, bufferList -> OSStatus in
            // Real-time audio thread: no allocation, no locks, just render.
            let buffers = UnsafeMutableAudioBufferListPointer(bufferList)
            guard let left = buffers[0].mData?.assumingMemoryBound(to: Float.self) else { return noErr }
            let right = buffers.count > 1
                ? buffers[1].mData?.assumingMemoryBound(to: Float.self) ?? left
                : left
            se_render(core, left, right, Int32(frameCount))
            return noErr
        }
        engine.attach(node)
        engine.connect(node, to: engine.mainMixerNode, format: format)
        sourceNode = node

        do {
            try engine.start()
        } catch {
            print("Audio engine failed to start: \(error)")
        }
    }

    private func observeSystemEvents() {
        let center = NotificationCenter.default

        // Output changed (e.g. headphones, Bluetooth): rebuild at the new sample rate.
        observers.append(center.addObserver(
            forName: .AVAudioEngineConfigurationChange, object: engine, queue: .main
        ) { [weak self] _ in
            self?.connectAndStart()
        })

        // Phone call, Siri, alarm: stop notes, then resume afterwards.
        observers.append(center.addObserver(
            forName: AVAudioSession.interruptionNotification, object: nil, queue: .main
        ) { [weak self] note in
            guard let self,
                  let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
            if type == .began {
                self.allNotesOff()
            } else {
                try? AVAudioSession.sharedInstance().setActive(true)
                self.connectAndStart()
            }
        })

        observers.append(center.addObserver(
            forName: AVAudioSession.mediaServicesWereResetNotification, object: nil, queue: .main
        ) { [weak self] _ in
            self?.configureSession()
            self?.connectAndStart()
        })
    }
}
