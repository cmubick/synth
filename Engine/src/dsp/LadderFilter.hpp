#pragma once

#include "DspMath.hpp"

namespace synth {

/*
 * 4-pole low-pass "ladder" filter in the style of the classic Moog design,
 * built with zero-delay-feedback one-pole stages so it stays stable while
 * the cutoff is swept fast. A soft clipper in the feedback path gives the
 * warm overdrive and lets it self-oscillate at full resonance.
 */
struct LadderFilter {
    float s[4] = {0, 0, 0, 0};

    void reset() { s[0] = s[1] = s[2] = s[3] = 0.0f; }

    /* g = tan(pi * cutoff / sampleRate), k = resonance 0..4 */
    float process(float x, float g, float k) {
        const float G = g / (1.0f + g);
        const float b = 1.0f / (1.0f + g);
        const float G2 = G * G;
        const float G3 = G2 * G;
        const float G4 = G3 * G;

        const float sigma = G3 * b * s[0] + G2 * b * s[1] + G * b * s[2] + b * s[3];
        float u = (x - k * sigma) / (1.0f + k * G4);
        u = softClip(u);

        float y = u;
        for (int i = 0; i < 4; ++i) {
            const float v = (y - s[i]) * G;
            const float out = v + s[i];
            s[i] = out + v;
            y = out;
        }
        return y;
    }
};

} // namespace synth
