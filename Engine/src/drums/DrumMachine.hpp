#pragma once

#include "synth_engine.h"
#include "DrumVoices.hpp"

#include <array>
#include <atomic>

namespace synth {

enum DrumTrack : int {
    kKick, kSnare, kClap, kLowTom, kHighTom, kRim, kCowbell, kClosedHat, kOpenHat, kCymbal,
    kDrumTrackCount
};
static_assert(kDrumTrackCount == SE_DRUM_TRACKS, "track count mismatch");

/*
 * The drum kit and its step sequencer.
 *
 * Settings and pattern steps are atomics, so the UI can edit them from its
 * own thread while the audio thread reads them. Everything under "audio
 * thread" is only touched while rendering.
 */
class DrumMachine {
public:
    static constexpr int kTracks = SE_DRUM_TRACKS;
    static constexpr int kSteps = SE_SEQ_STEPS;
    static constexpr int kPatterns = SE_SEQ_PATTERNS;
    static constexpr int kMaxChain = SE_SEQ_MAX_CHAIN;

    DrumMachine();
    void setSampleRate(float sampleRate);

    // MARK: Any thread
    void setParam(int track, se_drum_param p, float value);
    float param(int track, se_drum_param p) const;
    void setVolume(float v) { volume_.store(clampf(v, 0.0f, 1.0f), std::memory_order_relaxed); }
    float volume() const { return volume_.load(std::memory_order_relaxed); }
    uint32_t hitCount(int track) const;

    void setStep(int pattern, int track, int step, int value);
    int step(int pattern, int track, int step) const;
    void setLength(int pattern, int steps);
    int length(int pattern) const;
    void setTempo(float bpm) { tempo_.store(clampf(bpm, 40.0f, 240.0f), std::memory_order_relaxed); }
    float tempo() const { return tempo_.load(std::memory_order_relaxed); }
    void setSwing(float s) { swing_.store(clampf(s, 0.0f, 1.0f), std::memory_order_relaxed); }
    float swing() const { return swing_.load(std::memory_order_relaxed); }
    void setChain(const int32_t *patterns, int count);

    void play() { playRequested_.store(true, std::memory_order_release); }
    void stop() { playRequested_.store(false, std::memory_order_release); }
    bool isPlaying() const { return playRequested_.load(std::memory_order_acquire); }
    int currentStep() const { return uiStep_.load(std::memory_order_relaxed); }
    int currentPattern() const { return uiPattern_.load(std::memory_order_relaxed); }
    int chainPosition() const { return uiChainPos_.load(std::memory_order_relaxed); }
    uint32_t editCount() const { return editCount_.load(std::memory_order_relaxed); }

    // MARK: Audio thread
    void trigger(int track, float velocity);
    void recordHit(int track, bool accent);
    void beginBlock();
    void process(Random &rng, float &left, float &right);

private:
    void pullChain();
    void applyPendingChain();
    void advance();
    double stepLength(int stepIndex) const;
    std::atomic<uint8_t> &stepRef(int pattern, int track, int step) {
        return steps_[(pattern * kTracks + track) * kSteps + step];
    }

    // Shared settings
    std::array<std::atomic<float>, kTracks * SE_DRUM_PARAM_COUNT> params_;
    std::array<std::atomic<uint8_t>, kPatterns * kTracks * kSteps> steps_;
    std::array<std::atomic<uint8_t>, kPatterns> lengths_;
    std::atomic<float> tempo_{120.0f};
    std::atomic<float> swing_{0.0f};
    std::atomic<float> volume_{0.8f};
    std::atomic<bool> playRequested_{false};

    // Chain handoff (seqlock: version is odd while the UI is writing it)
    std::atomic<uint32_t> chainVersion_{0};
    std::array<std::atomic<int8_t>, kMaxChain> chainShared_;
    std::atomic<int> chainSharedLength_{1};

    // Readouts for the UI
    std::atomic<int> uiStep_{-1};
    std::atomic<int> uiPattern_{0};
    std::atomic<int> uiChainPos_{0};
    std::array<std::atomic<uint32_t>, kTracks> hitCounts_;
    std::atomic<uint32_t> editCount_{0};

    // Audio thread
    float sampleRate_ = 48000.0f;
    float invSampleRate_ = 1.0f / 48000.0f;
    bool playing_ = false;
    int step_ = -1;
    int pattern_ = 0;
    int chainPos_ = 0;
    int chain_[kMaxChain] = {0};
    int chainLength_ = 1;
    int pendingChain_[kMaxChain] = {0};
    int pendingLength_ = 1;
    bool chainPending_ = false;
    uint32_t seenChainVersion_ = 0;
    double samplesToNext_ = 0.0;
    double currentStepLength_ = 1.0;
    double blockStepBase_ = 6000.0;
    float blockSwing_ = 0.5f;
    int suppress_[kTracks];

    float gain_[kTracks] = {0};
    Kick kick_;
    Snare snare_;
    Clap clap_;
    Tom lowTom_{95.0f};
    Tom highTom_{165.0f};
    Rim rim_;
    Cowbell cowbell_;
    Metal closedHat_{0.06f, 9500.0f, 0.2f};
    Metal openHat_{0.45f, 9500.0f, 0.2f};
    Metal cymbal_{1.3f, 7000.0f, 0.35f};
};

const char *drumName(int track);
const char *drumShortName(int track);
const char *drumParamName(int track, se_drum_param p);
float drumParamDefault(int track, se_drum_param p);

} // namespace synth
