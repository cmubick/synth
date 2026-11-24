#pragma once

/*
 * 808-style drum sounds, made from scratch the way the original analog
 * machine made them: sine waves with pitch drops, filtered noise, and
 * clusters of square waves for the metallic sounds. No samples.
 *
 * Every voice takes the same three shaping knobs (0..1, 0.5 = classic):
 *   tune  - pitch, +/- one octave
 *   decay - length, roughly 0.35x .. 2.8x
 *   tone  - a per-drum color control (click, snap, brightness...)
 */

#include "../dsp/DspMath.hpp"
#include "../dsp/Svf.hpp"

#include <algorithm>

namespace synth {

struct DrumShape {
    float tune = 0.5f;
    float decay = 0.5f;
    float tone = 0.5f;

    float tuneFactor() const { return std::exp2((tune - 0.5f) * 2.0f); }
    float decayFactor() const { return std::exp2((decay - 0.5f) * 3.0f); }
};

/* Per-sample multiplier that fades to -60 dB in `seconds`. */
inline float fadeCoef(float seconds, float sampleRate) {
    return std::exp(-6.9f / std::max(1.0f, seconds * sampleRate));
}

inline float sine01(float phase) { return std::sin(kTwoPi * phase); }

inline void advancePhase(float &phase, float inc) {
    phase += inc;
    if (phase >= 1.0f) phase -= std::floor(phase);
}

constexpr float kSilent = 1e-4f;

// Bass drum: a sine whose pitch swoops down fast, plus a little click.
class Kick {
public:
    void trigger(const DrumShape &s, float sr) {
        phase_ = 0.0f;
        amp_ = pitch_ = click_ = 1.0f;
        freq_ = 49.0f * s.tuneFactor();
        ampCoef_ = fadeCoef(0.55f * s.decayFactor(), sr);
        pitchCoef_ = fadeCoef(0.05f, sr);
        clickCoef_ = fadeCoef(0.005f, sr);
        clickAmount_ = 0.15f + s.tone * 0.6f;
        drive_ = 1.0f + s.tone * 2.0f;
    }

    float process(Random &rng, float invSr) {
        if (amp_ < kSilent) return 0.0f;
        advancePhase(phase_, freq_ * (1.0f + 3.0f * pitch_) * invSr);
        const float s = sine01(phase_) * amp_ + rng.bipolar() * click_ * clickAmount_;
        amp_ *= ampCoef_;
        pitch_ *= pitchCoef_;
        click_ *= clickCoef_;
        return softClip(s * drive_);
    }

private:
    float phase_ = 0, amp_ = 0, pitch_ = 0, click_ = 0, freq_ = 50;
    float ampCoef_ = 0, pitchCoef_ = 0, clickCoef_ = 0, clickAmount_ = 0, drive_ = 1;
};

// Snare: two tuned drum-head tones plus bright noise (the "snares").
class Snare {
public:
    void trigger(const DrumShape &s, float sr) {
        p1_ = p2_ = 0.0f;
        body_ = noise_ = 1.0f;
        f1_ = 180.0f * s.tuneFactor();
        f2_ = 330.0f * s.tuneFactor();
        bodyCoef_ = fadeCoef(0.12f * std::sqrt(s.decayFactor()), sr);
        noiseCoef_ = fadeCoef(0.22f * s.decayFactor(), sr);
        snappy_ = 0.3f + 0.9f * s.tone;
        hp_.set(1600.0f, 0.7f, sr);
    }

    float process(Random &rng, float invSr) {
        if (body_ < kSilent && noise_ < kSilent) return 0.0f;
        advancePhase(p1_, f1_ * invSr);
        advancePhase(p2_, f2_ * invSr);
        const float body = (sine01(p1_) + 0.5f * sine01(p2_)) * body_ * 0.6f;
        const float noise = hp_.highpass(rng.bipolar()) * noise_ * snappy_;
        body_ *= bodyCoef_;
        noise_ *= noiseCoef_;
        return softClip((body + noise) * 1.3f);
    }

private:
    float p1_ = 0, p2_ = 0, f1_ = 180, f2_ = 330;
    float body_ = 0, noise_ = 0, bodyCoef_ = 0, noiseCoef_ = 0, snappy_ = 1;
    Svf hp_;
};

// Hand clap: a few rapid bursts of filtered noise, then a short tail.
class Clap {
public:
    void trigger(const DrumShape &s, float sr) {
        t_ = 0;
        burstLen_ = std::max(1, static_cast<int>(0.0105f * sr));
        env_ = 1.0f;
        tail_ = 0.0f;
        active_ = true;
        burstCoef_ = fadeCoef(0.014f, sr);
        tailCoef_ = fadeCoef(0.32f * s.decayFactor(), sr);
        bp_.set(1150.0f * s.tuneFactor(), 0.8f + s.tone * 2.5f, sr);
    }

    float process(Random &rng, float) {
        if (!active_) return 0.0f;
        if (t_ < 3 * burstLen_) {
            if (t_ % burstLen_ == 0) env_ = 1.0f;
            env_ *= burstCoef_;
        } else {
            if (t_ == 3 * burstLen_) tail_ = 0.85f;
            env_ = tail_;
            tail_ *= tailCoef_;
            if (tail_ < kSilent) active_ = false;
        }
        ++t_;
        return softClip(bp_.bandpass(rng.bipolar()) * env_ * 3.0f);
    }

private:
    int t_ = 0, burstLen_ = 500;
    float env_ = 0, tail_ = 0, burstCoef_ = 0, tailCoef_ = 0;
    bool active_ = false;
    Svf bp_;
};

// Tom: a sine with a gentle pitch drop and a touch of noise.
class Tom {
public:
    explicit Tom(float baseHz) : baseHz_(baseHz) {}

    void trigger(const DrumShape &s, float sr) {
        phase_ = 0.0f;
        amp_ = pitch_ = noise_ = 1.0f;
        freq_ = baseHz_ * s.tuneFactor();
        ampCoef_ = fadeCoef(0.5f * s.decayFactor(), sr);
        pitchCoef_ = fadeCoef(0.15f, sr);
        noiseCoef_ = fadeCoef(0.04f, sr);
        noiseAmount_ = s.tone * 0.35f;
    }

    float process(Random &rng, float invSr) {
        if (amp_ < kSilent) return 0.0f;
        advancePhase(phase_, freq_ * (1.0f + 0.5f * pitch_) * invSr);
        const float s = (sine01(phase_) + rng.bipolar() * noise_ * noiseAmount_) * amp_;
        amp_ *= ampCoef_;
        pitch_ *= pitchCoef_;
        noise_ *= noiseCoef_;
        return softClip(s * 1.2f);
    }

private:
    float baseHz_;
    float phase_ = 0, amp_ = 0, pitch_ = 0, noise_ = 0, freq_ = 100;
    float ampCoef_ = 0, pitchCoef_ = 0, noiseCoef_ = 0, noiseAmount_ = 0;
};

// Rim shot: two short, high "tock" tones.
class Rim {
public:
    void trigger(const DrumShape &s, float sr) {
        p1_ = p2_ = 0.0f;
        amp_ = 1.0f;
        f1_ = 480.0f * s.tuneFactor();
        f2_ = 1700.0f * s.tuneFactor();
        ampCoef_ = fadeCoef(0.04f * s.decayFactor(), sr);
        drive_ = 1.5f + s.tone * 3.0f;
        hp_.set(350.0f, 0.7f, sr);
    }

    float process(Random &, float invSr) {
        if (amp_ < kSilent) return 0.0f;
        advancePhase(p1_, f1_ * invSr);
        advancePhase(p2_, f2_ * invSr);
        const float s = hp_.highpass((0.5f * sine01(p1_) + sine01(p2_)) * amp_);
        amp_ *= ampCoef_;
        return softClip(s * drive_) * 0.8f;
    }

private:
    float p1_ = 0, p2_ = 0, f1_ = 480, f2_ = 1700, amp_ = 0, ampCoef_ = 0, drive_ = 1;
    Svf hp_;
};

// Cowbell: two detuned square waves through a band-pass, quick then slow fade.
class Cowbell {
public:
    void trigger(const DrumShape &s, float sr) {
        p1_ = p2_ = 0.0f;
        fast_ = slow_ = 1.0f;
        f1_ = 540.0f * s.tuneFactor();
        f2_ = 800.0f * s.tuneFactor();
        fastCoef_ = fadeCoef(0.035f, sr);
        slowCoef_ = fadeCoef(0.4f * s.decayFactor(), sr);
        bp_.set((700.0f + 1400.0f * s.tone) * s.tuneFactor(), 1.4f, sr);
    }

    float process(Random &, float invSr) {
        if (slow_ < kSilent) return 0.0f;
        advancePhase(p1_, f1_ * invSr);
        advancePhase(p2_, f2_ * invSr);
        const float sq = ((p1_ < 0.5f ? 1.0f : -1.0f) + (p2_ < 0.5f ? 1.0f : -1.0f)) * 0.5f;
        const float env = 0.65f * fast_ + 0.35f * slow_;
        fast_ *= fastCoef_;
        slow_ *= slowCoef_;
        return softClip(bp_.bandpass(sq) * env * 1.8f);
    }

private:
    float p1_ = 0, p2_ = 0, f1_ = 540, f2_ = 800, fast_ = 0, slow_ = 0, fastCoef_ = 0, slowCoef_ = 0;
    Svf bp_;
};

// Hi-hats and cymbal: six square waves at clashing pitches = metal.
class Metal {
public:
    void trigger(const DrumShape &s, float sr) {
        amp_ = 1.0f;
        tune_ = s.tuneFactor();
        ampCoef_ = fadeCoef(decaySeconds_ * s.decayFactor(), sr);
        bp_.set(centerHz_ * (0.7f + 0.6f * s.tone), 1.1f, sr);
        hp_.set(centerHz_ * 0.55f, 0.7f, sr);
    }

    /* Open hat is cut short when the closed hat plays, like a real hi-hat. */
    void choke(float sr) { ampCoef_ = fadeCoef(0.02f, sr); }

    float process(Random &rng, float invSr) {
        if (amp_ < kSilent) return 0.0f;
        static constexpr float kRatios[6] = {205.3f, 304.4f, 369.6f, 522.7f, 540.0f, 800.0f};
        float metal = 0.0f;
        for (int i = 0; i < 6; ++i) {
            advancePhase(phases_[i], kRatios[i] * tune_ * invSr);
            metal += phases_[i] < 0.5f ? 1.0f : -1.0f;
        }
        const float src = metal * (1.0f / 6.0f) * (1.0f - noiseMix_) + rng.bipolar() * noiseMix_;
        const float s = hp_.highpass(bp_.bandpass(src)) * amp_;
        amp_ *= ampCoef_;
        return softClip(s * 5.0f);
    }

    Metal(float decaySeconds, float centerHz, float noiseMix)
        : decaySeconds_(decaySeconds), centerHz_(centerHz), noiseMix_(noiseMix) {}

private:
    float decaySeconds_, centerHz_, noiseMix_;
    float phases_[6] = {0.0f, 0.13f, 0.37f, 0.61f, 0.29f, 0.83f};
    float amp_ = 0, ampCoef_ = 0, tune_ = 1;
    Svf bp_, hp_;
};

} // namespace synth
