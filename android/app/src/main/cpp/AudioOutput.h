// Plays the shared engine through Android's AAudio: the Android counterpart of
// iOS/Synth/Audio/AudioEngine.swift. The engine does all the sound; this only
// asks it for samples on the audio thread and keeps the stream alive when the
// output changes (headphones in or out, Bluetooth, etc.).
#pragma once

#include "synth_engine.h"

#include <aaudio/AAudio.h>

#include <atomic>
#include <mutex>
#include <vector>

class AudioOutput {
public:
    explicit AudioOutput(se_engine *engine) : engine_(engine) {}
    ~AudioOutput() { stop(); }

    /** Opens and starts the stream. False if the phone has no usable output. */
    bool start();
    /** Stops and closes the stream. Safe to call when already stopped. */
    void stop();

    int32_t sampleRate() const { return sampleRate_.load(); }
    int32_t framesPerBurst() const { return framesPerBurst_.load(); }

private:
    static aaudio_data_callback_result_t onAudio(AAudioStream *stream, void *user, void *data, int32_t frames);
    static void onError(AAudioStream *stream, void *user, aaudio_result_t error);

    bool openLocked();
    void closeLocked();
    void restartAfterDisconnect();

    se_engine *engine_;
    AAudioStream *stream_ = nullptr;
    std::mutex lock_;
    std::atomic<bool> wanted_{false};
    std::atomic<int32_t> sampleRate_{0};
    std::atomic<int32_t> framesPerBurst_{0};

    // Scratch buffers for the engine's left and right channels, sized when the
    // stream opens so the audio thread never allocates.
    std::vector<float> left_, right_;
};
