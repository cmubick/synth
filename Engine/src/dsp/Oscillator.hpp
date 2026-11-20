#pragma once

#include "DspMath.hpp"

namespace synth {

enum Waveform : int { kSaw = 0, kSquare = 1, kTriangle = 2, kSine = 3 };

/*
 * Band-limited oscillator using PolyBLEP to smooth the sharp edges of the
 * saw and square waves, which keeps high notes from sounding harsh.
 */
struct Oscillator {
    float phase = 0.0f; // 0..1

    static float polyBlep(float t, float dt) {
        if (t < dt) {
            t /= dt;
            return t + t - t * t - 1.0f;
        }
        if (t > 1.0f - dt) {
            t = (t - 1.0f) / dt;
            return t * t + t + t + 1.0f;
        }
        return 0.0f;
    }

    /* dt = frequency / sampleRate. */
    float process(int wave, float dt) {
        dt = clampf(dt, 0.0f, 0.49f);
        const float t = phase;
        float out;
        switch (wave) {
        case kSaw:
            out = 2.0f * t - 1.0f - polyBlep(t, dt);
            break;
        case kSquare: {
            out = t < 0.5f ? 1.0f : -1.0f;
            out += polyBlep(t, dt);
            float t2 = t + 0.5f;
            if (t2 >= 1.0f) t2 -= 1.0f;
            out -= polyBlep(t2, dt);
            break;
        }
        case kTriangle:
            out = 1.0f - 4.0f * std::fabs(t - 0.5f);
            break;
        default:
            out = std::sin(kTwoPi * t);
            break;
        }
        phase += dt;
        if (phase >= 1.0f) phase -= 1.0f;
        return out;
    }
};

} // namespace synth
