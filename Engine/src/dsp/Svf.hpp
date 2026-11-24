#pragma once

#include "DspMath.hpp"

namespace synth {

/*
 * State-variable filter (zero-delay-feedback form). One input gives
 * low-pass, band-pass and high-pass outputs at once. Used to shape noise
 * and metallic tones in the drum voices.
 */
struct Svf {
    float ic1 = 0.0f, ic2 = 0.0f;
    float k = 1.0f, a1 = 0.0f, a2 = 0.0f, a3 = 0.0f;

    void set(float hz, float q, float sampleRate) {
        const float g = std::tan(kPi * clampf(hz, 10.0f, sampleRate * 0.45f) / sampleRate);
        k = 1.0f / (q > 0.05f ? q : 0.05f);
        a1 = 1.0f / (1.0f + g * (g + k));
        a2 = g * a1;
        a3 = g * a2;
    }

    void reset() { ic1 = ic2 = 0.0f; }

    void process(float x, float &lp, float &bp, float &hp) {
        const float v3 = x - ic2;
        const float v1 = a1 * ic1 + a2 * v3;
        const float v2 = ic2 + a2 * ic1 + a3 * v3;
        ic1 = 2.0f * v1 - ic1;
        ic2 = 2.0f * v2 - ic2;
        lp = v2;
        bp = v1;
        hp = x - k * v1 - v2;
    }

    float bandpass(float x) {
        float lp, bp, hp;
        process(x, lp, bp, hp);
        return bp;
    }

    float highpass(float x) {
        float lp, bp, hp;
        process(x, lp, bp, hp);
        return hp;
    }
};

} // namespace synth
