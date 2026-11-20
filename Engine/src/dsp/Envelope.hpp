#pragma once

#include "DspMath.hpp"
#include <algorithm>

namespace synth {

/*
 * Analog-style ADSR. Attack curves toward an overshoot target like a
 * charging capacitor; decay and release fall exponentially. Retriggering
 * starts from the current level so there's no click.
 */
struct Envelope {
    enum Stage { kIdle, kAttack, kDecay, kSustain, kRelease };

    Stage stage = kIdle;
    float level = 0.0f;
    float attackCoef = 0.01f;
    float decayCoef = 0.999f;
    float releaseCoef = 0.999f;
    float sustain = 0.8f;

    static constexpr float kAttackTarget = 1.3f;

    void set(float attackSec, float decaySec, float sustainLevel, float releaseSec, float sampleRate) {
        // Time to rise 0 -> 1 when aiming at 1.3 is tau * ln(1.3 / 0.3).
        const float tau = std::max(attackSec / 1.466f, 0.0002f);
        attackCoef = 1.0f - std::exp(-1.0f / (tau * sampleRate));
        decayCoef = std::exp(-6.9f / std::max(1.0f, decaySec * sampleRate));
        releaseCoef = std::exp(-6.9f / std::max(1.0f, releaseSec * sampleRate));
        sustain = sustainLevel;
    }

    void gateOn() { stage = kAttack; }
    void gateOff() {
        if (stage != kIdle) stage = kRelease;
    }
    void kill() {
        stage = kIdle;
        level = 0.0f;
    }
    bool active() const { return stage != kIdle; }

    float process() {
        switch (stage) {
        case kAttack:
            level += (kAttackTarget - level) * attackCoef;
            if (level >= 1.0f) {
                level = 1.0f;
                stage = kDecay;
            }
            break;
        case kDecay:
            level = sustain + (level - sustain) * decayCoef;
            if (std::fabs(level - sustain) < 1e-4f) {
                level = sustain;
                stage = kSustain;
            }
            break;
        case kSustain:
            // Follow the sustain knob smoothly if it moves while holding.
            level = sustain + (level - sustain) * 0.999f;
            break;
        case kRelease:
            level *= releaseCoef;
            if (level < 1e-4f) kill();
            break;
        case kIdle:
            break;
        }
        return level;
    }
};

} // namespace synth
