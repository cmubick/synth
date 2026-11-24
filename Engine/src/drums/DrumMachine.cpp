#include "DrumMachine.hpp"

#include <cstdio>

namespace synth {

namespace {

struct TrackInfo {
    const char *name;
    const char *shortName;
    const char *toneName;
    float level;
    float pan; // -1 left .. 1 right
};

const TrackInfo kTrackInfo[kDrumTrackCount] = {
    {"Kick", "BD", "Click", 0.85f, 0.0f},
    {"Snare", "SD", "Snap", 0.75f, 0.0f},
    {"Clap", "CP", "Ring", 0.7f, 0.1f},
    {"Low Tom", "LT", "Noise", 0.72f, -0.35f},
    {"High Tom", "HT", "Noise", 0.68f, 0.35f},
    {"Rim", "RS", "Crack", 0.6f, 0.2f},
    {"Cowbell", "CB", "Tone", 0.55f, -0.2f},
    {"Closed Hat", "CH", "Bright", 0.62f, 0.25f},
    {"Open Hat", "OH", "Bright", 0.58f, 0.3f},
    {"Cymbal", "CY", "Bright", 0.5f, -0.3f},
};

constexpr float kNormalVelocity = 0.72f;
constexpr float kAccentVelocity = 1.0f;

bool validTrack(int t) { return t >= 0 && t < kDrumTrackCount; }
bool validPattern(int p) { return p >= 0 && p < DrumMachine::kPatterns; }
bool validStep(int s) { return s >= 0 && s < DrumMachine::kSteps; }

} // namespace

const char *drumName(int track) { return validTrack(track) ? kTrackInfo[track].name : ""; }
const char *drumShortName(int track) { return validTrack(track) ? kTrackInfo[track].shortName : ""; }

const char *drumParamName(int track, se_drum_param p) {
    switch (p) {
    case SE_DRUM_LEVEL: return "Level";
    case SE_DRUM_TUNE: return "Tune";
    case SE_DRUM_DECAY: return "Decay";
    case SE_DRUM_TONE: return validTrack(track) ? kTrackInfo[track].toneName : "Tone";
    default: return "";
    }
}

float drumParamDefault(int track, se_drum_param p) {
    if (p == SE_DRUM_LEVEL && validTrack(track)) return kTrackInfo[track].level;
    return 0.5f;
}

DrumMachine::DrumMachine() {
    for (int t = 0; t < kTracks; ++t) {
        for (int p = 0; p < SE_DRUM_PARAM_COUNT; ++p) {
            params_[t * SE_DRUM_PARAM_COUNT + p].store(drumParamDefault(t, static_cast<se_drum_param>(p)));
        }
        hitCounts_[t].store(0);
        suppress_[t] = -1;
    }
    for (auto &s : steps_) s.store(0);
    for (auto &l : lengths_) l.store(kSteps);
    for (auto &c : chainShared_) c.store(0);
}

void DrumMachine::setSampleRate(float sampleRate) {
    sampleRate_ = sampleRate;
    invSampleRate_ = 1.0f / sampleRate;
}

// MARK: - Settings (any thread)

void DrumMachine::setParam(int track, se_drum_param p, float value) {
    if (!validTrack(track) || p < 0 || p >= SE_DRUM_PARAM_COUNT) return;
    params_[track * SE_DRUM_PARAM_COUNT + p].store(clampf(value, 0.0f, 1.0f), std::memory_order_relaxed);
}

float DrumMachine::param(int track, se_drum_param p) const {
    if (!validTrack(track) || p < 0 || p >= SE_DRUM_PARAM_COUNT) return 0.0f;
    return params_[track * SE_DRUM_PARAM_COUNT + p].load(std::memory_order_relaxed);
}

uint32_t DrumMachine::hitCount(int track) const {
    return validTrack(track) ? hitCounts_[track].load(std::memory_order_relaxed) : 0;
}

void DrumMachine::setStep(int pattern, int track, int s, int value) {
    if (!validPattern(pattern) || !validTrack(track) || !validStep(s)) return;
    const uint8_t v = static_cast<uint8_t>(value < 0 ? 0 : (value > 2 ? 2 : value));
    stepRef(pattern, track, s).store(v, std::memory_order_relaxed);
}

int DrumMachine::step(int pattern, int track, int s) const {
    if (!validPattern(pattern) || !validTrack(track) || !validStep(s)) return 0;
    return steps_[(pattern * kTracks + track) * kSteps + s].load(std::memory_order_relaxed);
}

void DrumMachine::setLength(int pattern, int steps) {
    if (!validPattern(pattern)) return;
    lengths_[pattern].store(static_cast<uint8_t>(steps < 1 ? 1 : (steps > kSteps ? kSteps : steps)),
                            std::memory_order_relaxed);
}

int DrumMachine::length(int pattern) const {
    return validPattern(pattern) ? lengths_[pattern].load(std::memory_order_relaxed) : kSteps;
}

void DrumMachine::setChain(const int32_t *patterns, int count) {
    if (!patterns || count < 1) return;
    if (count > kMaxChain) count = kMaxChain;
    const uint32_t v = chainVersion_.load(std::memory_order_relaxed);
    chainVersion_.store(v + 1, std::memory_order_relaxed); // odd: writing
    std::atomic_thread_fence(std::memory_order_release);
    for (int i = 0; i < count; ++i) {
        const int p = validPattern(patterns[i]) ? patterns[i] : 0;
        chainShared_[i].store(static_cast<int8_t>(p), std::memory_order_relaxed);
    }
    chainSharedLength_.store(count, std::memory_order_relaxed);
    chainVersion_.store(v + 2, std::memory_order_release); // even: done
}

// MARK: - Audio thread

void DrumMachine::trigger(int track, float velocity) {
    if (!validTrack(track)) return;
    DrumShape shape;
    shape.tune = param(track, SE_DRUM_TUNE);
    shape.decay = param(track, SE_DRUM_DECAY);
    shape.tone = param(track, SE_DRUM_TONE);
    const float level = param(track, SE_DRUM_LEVEL);
    gain_[track] = level * level * velocity;

    const float sr = sampleRate_;
    switch (track) {
    case kKick: kick_.trigger(shape, sr); break;
    case kSnare: snare_.trigger(shape, sr); break;
    case kClap: clap_.trigger(shape, sr); break;
    case kLowTom: lowTom_.trigger(shape, sr); break;
    case kHighTom: highTom_.trigger(shape, sr); break;
    case kRim: rim_.trigger(shape, sr); break;
    case kCowbell: cowbell_.trigger(shape, sr); break;
    case kClosedHat:
        closedHat_.trigger(shape, sr);
        openHat_.choke(sr);
        break;
    case kOpenHat: openHat_.trigger(shape, sr); break;
    case kCymbal: cymbal_.trigger(shape, sr); break;
    default: break;
    }
    hitCounts_[track].fetch_add(1, std::memory_order_relaxed);
}

void DrumMachine::recordHit(int track, bool accent) {
    if (!validTrack(track)) return;
    trigger(track, accent ? kAccentVelocity : kNormalVelocity);
    if (!playing_ || step_ < 0) return;

    // Snap to whichever step is closest: this one, or the next.
    const double progress = 1.0 - samplesToNext_ / currentStepLength_;
    int target = step_;
    if (progress >= 0.5) {
        target = step_ + 1;
        if (target >= length(pattern_)) target = 0;
        suppress_[track] = target; // we already heard it; don't double it
    }
    stepRef(pattern_, track, target).store(accent ? 2 : 1, std::memory_order_relaxed);
    editCount_.fetch_add(1, std::memory_order_relaxed);
}

void DrumMachine::pullChain() {
    const uint32_t v = chainVersion_.load(std::memory_order_acquire);
    if (v == seenChainVersion_ || (v & 1u)) return;

    int tmp[kMaxChain];
    int len = chainSharedLength_.load(std::memory_order_relaxed);
    if (len < 1) len = 1;
    if (len > kMaxChain) len = kMaxChain;
    for (int i = 0; i < len; ++i) tmp[i] = chainShared_[i].load(std::memory_order_relaxed);
    std::atomic_thread_fence(std::memory_order_acquire);
    if (chainVersion_.load(std::memory_order_relaxed) != v) return; // UI was mid-write; retry next block

    for (int i = 0; i < len; ++i) pendingChain_[i] = tmp[i];
    pendingLength_ = len;
    chainPending_ = true;
    seenChainVersion_ = v;
}

void DrumMachine::applyPendingChain() {
    for (int i = 0; i < pendingLength_; ++i) chain_[i] = pendingChain_[i];
    chainLength_ = pendingLength_;
    chainPos_ = 0;
    chainPending_ = false;
}

double DrumMachine::stepLength(int stepIndex) const {
    // Swing stretches the on-beat 16th and shortens the off-beat one.
    const double pair = 2.0 * blockStepBase_;
    const double ratio = 0.5 + 0.25 * blockSwing_;
    return (stepIndex % 2 == 0) ? pair * ratio : pair * (1.0 - ratio);
}

void DrumMachine::beginBlock() {
    pullChain();
    blockStepBase_ = sampleRate_ * 60.0 / (tempo() * 4.0);
    blockSwing_ = swing();

    const bool wantPlay = playRequested_.load(std::memory_order_acquire);
    if (wantPlay && !playing_) {
        if (chainPending_) applyPendingChain();
        playing_ = true;
        chainPos_ = 0;
        pattern_ = chain_[0];
        step_ = -1;
        samplesToNext_ = 0.0;
        for (int &s : suppress_) s = -1;
    } else if (!wantPlay && playing_) {
        playing_ = false;
        step_ = -1;
        uiStep_.store(-1, std::memory_order_relaxed);
    }

    if (!playing_ && chainPending_) {
        applyPendingChain();
        pattern_ = chain_[0];
    }
    if (!playing_) {
        uiPattern_.store(pattern_, std::memory_order_relaxed);
        uiChainPos_.store(chainPos_, std::memory_order_relaxed);
    }
}

void DrumMachine::advance() {
    ++step_;
    if (step_ >= length(pattern_)) {
        step_ = 0;
        if (chainPending_) {
            applyPendingChain();
        } else {
            chainPos_ = (chainPos_ + 1) % chainLength_;
        }
        pattern_ = chain_[chainPos_];
    }

    for (int t = 0; t < kTracks; ++t) {
        const int v = stepRef(pattern_, t, step_).load(std::memory_order_relaxed);
        if (suppress_[t] == step_) {
            suppress_[t] = -1;
            continue;
        }
        if (v) trigger(t, v == 2 ? kAccentVelocity : kNormalVelocity);
    }

    uiStep_.store(step_, std::memory_order_relaxed);
    uiPattern_.store(pattern_, std::memory_order_relaxed);
    uiChainPos_.store(chainPos_, std::memory_order_relaxed);
}

void DrumMachine::process(Random &rng, float &left, float &right) {
    if (playing_) {
        // Fire on the exact sample the step is due, then count down.
        if (samplesToNext_ <= 0.0) {
            advance();
            currentStepLength_ = stepLength(step_);
            samplesToNext_ += currentStepLength_;
        }
        samplesToNext_ -= 1.0;
    }

    const float invSr = invSampleRate_;
    const float voices[kDrumTrackCount] = {
        kick_.process(rng, invSr),    snare_.process(rng, invSr),     clap_.process(rng, invSr),
        lowTom_.process(rng, invSr),  highTom_.process(rng, invSr),   rim_.process(rng, invSr),
        cowbell_.process(rng, invSr), closedHat_.process(rng, invSr), openHat_.process(rng, invSr),
        cymbal_.process(rng, invSr),
    };

    float l = 0.0f, r = 0.0f;
    for (int t = 0; t < kDrumTrackCount; ++t) {
        const float s = voices[t] * gain_[t];
        const float pan = kTrackInfo[t].pan;
        l += s * (pan > 0.0f ? 1.0f - pan : 1.0f);
        r += s * (pan < 0.0f ? 1.0f + pan : 1.0f);
    }
    const float v = volume();
    const float busGain = v * v * 2.2f;
    left = l * busGain;
    right = r * busGain;
}

} // namespace synth
