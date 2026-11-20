#pragma once

#include "DspMath.hpp"
#include <vector>

namespace synth {

/*
 * Ping-pong delay: echoes bounce left, right, left... A gentle low-pass in
 * the feedback loop darkens each repeat like a tape echo. Changing the
 * time while it plays bends the pitch of the echoes, also like tape.
 */
struct StereoDelay {
    std::vector<float> left, right;
    int size = 0;
    int writeIndex = 0;
    float toneL = 0.0f, toneR = 0.0f;

    void prepare(float sampleRate, float maxSeconds) {
        size = static_cast<int>(sampleRate * maxSeconds) + 4;
        left.assign(size, 0.0f);
        right.assign(size, 0.0f);
        writeIndex = 0;
        toneL = toneR = 0.0f;
    }

    void process(float in, float delaySamples, float feedback, float mix, float &outL, float &outR) {
        delaySamples = clampf(delaySamples, 1.0f, static_cast<float>(size - 2));
        float readPos = static_cast<float>(writeIndex) - delaySamples;
        if (readPos < 0.0f) readPos += static_cast<float>(size);
        const int i0 = static_cast<int>(readPos);
        const int i1 = (i0 + 1) % size;
        const float frac = readPos - static_cast<float>(i0);

        const float dl = left[i0] + (left[i1] - left[i0]) * frac;
        const float dr = right[i0] + (right[i1] - right[i0]) * frac;

        toneL += (dr - toneL) * 0.45f;
        toneR += (dl - toneR) * 0.45f;

        float wl = in + toneL * feedback;
        float wr = toneR * feedback;
        if (std::fabs(wl) < 1e-15f) wl = 0.0f; // avoid denormals in long tails
        if (std::fabs(wr) < 1e-15f) wr = 0.0f;
        left[writeIndex] = softClip(wl);
        right[writeIndex] = softClip(wr);
        if (++writeIndex >= size) writeIndex = 0;

        outL = in + dl * mix;
        outR = in + dr * mix;
    }
};

} // namespace synth
