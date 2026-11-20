#pragma once

#include <cmath>
#include <cstdint>

namespace synth {

constexpr float kPi = 3.14159265358979f;
constexpr float kTwoPi = 2.0f * kPi;

inline float clampf(float x, float lo, float hi) {
    return x < lo ? lo : (x > hi ? hi : x);
}

inline float midiToHz(float note) {
    return 440.0f * std::exp2((note - 69.0f) / 12.0f);
}

/* Cheap tanh-like soft clipper, exact enough for audio saturation. */
inline float softClip(float x) {
    x = clampf(x, -3.0f, 3.0f);
    const float x2 = x * x;
    return x * (27.0f + x2) / (27.0f + 9.0f * x2);
}

/* One-pole smoothing coefficient for a time constant in seconds. */
inline float smoothingCoef(float seconds, float sampleRate) {
    return 1.0f - std::exp(-1.0f / (seconds * sampleRate));
}

/* Small fast random generator for noise and sample & hold. */
struct Random {
    uint32_t state = 0x9E3779B9u;
    float bipolar() {
        state ^= state << 13;
        state ^= state >> 17;
        state ^= state << 5;
        return static_cast<float>(state) * (2.0f / 4294967295.0f) - 1.0f;
    }
};

/*
 * Knob value (0..1) to real units. Used by the engine and by the text
 * formatter, so what the UI shows always matches what you hear.
 */
namespace mapping {
inline float cutoffHz(float v) { return 20.0f * std::pow(1000.0f, v); }
inline float envSeconds(float v) { return 0.001f * std::pow(5000.0f, v); }
inline float lfoHz(float v) { return 0.05f * std::pow(400.0f, v); }
inline float glideSeconds(float v) { return v * v * 1.5f; }
inline float delaySeconds(float v) { return 0.03f + v * v * 1.17f; }
inline float volumeGain(float v) { return v * v * 1.6f; }
} // namespace mapping

} // namespace synth
