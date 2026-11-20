#include "SynthEngine.hpp"

#include <algorithm>

namespace synth {

namespace {
constexpr float kMaxDelaySeconds = 1.3f;
inline int toIndex(float v) { return static_cast<int>(v + 0.5f); }
} // namespace

SynthEngine::SynthEngine(double sampleRate) {
    for (int i = 0; i < SE_PARAM_COUNT; ++i) {
        params_[i].store(paramInfo(static_cast<se_param>(i)).default_value);
    }
    // Free-running oscillators start at different phases, like analog ones.
    for (auto &v : voices_) {
        v.osc1.phase = 0.5f + 0.5f * random_.bipolar();
        v.osc2.phase = 0.5f + 0.5f * random_.bipolar();
        v.sub.phase = 0.5f + 0.5f * random_.bipolar();
    }
    setSampleRate(sampleRate);
}

void SynthEngine::setSampleRate(double sampleRate) {
    sampleRate_ = static_cast<float>(sampleRate > 1000.0 ? sampleRate : 48000.0);
    invSampleRate_ = 1.0f / sampleRate_;
    smoothCoef_ = smoothingCoef(0.01f, sampleRate_);
    delaySmoothCoef_ = smoothingCoef(0.15f, sampleRate_);
    delay_.prepare(sampleRate_, kMaxDelaySeconds);

    cutoffSmoothed_ = p(SE_FILTER_CUTOFF);
    volumeSmoothed_ = mapping::volumeGain(p(SE_MASTER_VOLUME));
    delayTimeSmoothed_ = mapping::delaySeconds(p(SE_DELAY_TIME));

    for (auto &v : voices_) {
        v.ampEnv.kill();
        v.filterEnv.kill();
        v.filter.reset();
        v.gate = false;
    }
    stackSize_ = 0;
}

void SynthEngine::noteOn(int note, float velocity) {
    if (note < 0 || note > 127) return;
    events_.push({NoteEvent::kNoteOn, static_cast<int8_t>(note), clampf(velocity, 0.0f, 1.0f)});
}

void SynthEngine::noteOff(int note) {
    if (note < 0 || note > 127) return;
    events_.push({NoteEvent::kNoteOff, static_cast<int8_t>(note), 0.0f});
}

void SynthEngine::allNotesOff() { events_.push({NoteEvent::kAllOff, 0, 0.0f}); }

void SynthEngine::setParam(se_param id, float value) {
    if (id < 0 || id >= SE_PARAM_COUNT) return;
    const se_param_info &info = paramInfo(id);
    value = clampf(value, info.min_value, info.max_value);
    if (info.steps > 0) value = std::round(value);
    params_[id].store(value, std::memory_order_relaxed);
}

float SynthEngine::param(se_param id) const {
    if (id < 0 || id >= SE_PARAM_COUNT) return 0.0f;
    return p(id);
}

// MARK: - Note handling (audio thread)

void SynthEngine::handle(const NoteEvent &e) {
    const bool mono = toIndex(p(SE_VOICE_MODE)) == 1;
    switch (e.type) {
    case NoteEvent::kNoteOn:
        mono ? monoNoteOn(e.note, e.velocity) : polyNoteOn(e.note, e.velocity);
        break;
    case NoteEvent::kNoteOff:
        mono ? monoNoteOff(e.note) : polyNoteOff(e.note);
        break;
    case NoteEvent::kAllOff:
        releaseAll();
        break;
    }
}

void SynthEngine::startVoice(Voice &v, int note, float velocity, bool fromSilence) {
    if (fromSilence) {
        v.filter.reset();
        v.currentNote = static_cast<float>(note);
    }
    v.note = note;
    v.targetNote = static_cast<float>(note);
    v.gate = true;
    v.age = ++voiceCounter_;
    v.velocityGain = 0.35f + 0.65f * velocity;
    v.ampEnv.gateOn();
    v.filterEnv.gateOn();
}

void SynthEngine::polyNoteOn(int note, float velocity) {
    Voice *chosen = nullptr;

    // Same key again: retrigger the voice already playing it.
    for (auto &v : voices_) {
        if (v.active() && v.note == note) {
            chosen = &v;
            break;
        }
    }
    // Otherwise a silent voice.
    if (!chosen) {
        for (auto &v : voices_) {
            if (!v.active()) {
                chosen = &v;
                break;
            }
        }
    }
    // Otherwise steal: the oldest released voice, else the oldest held one.
    if (!chosen) {
        for (auto &v : voices_) {
            if (!v.gate && (!chosen || v.age < chosen->age)) chosen = &v;
        }
    }
    if (!chosen) {
        for (auto &v : voices_) {
            if (!chosen || v.age < chosen->age) chosen = &v;
        }
    }

    const bool fromSilence = !chosen->active();
    startVoice(*chosen, note, velocity, fromSilence);
    chosen->currentNote = static_cast<float>(note); // no glide in poly mode
}

void SynthEngine::polyNoteOff(int note) {
    for (auto &v : voices_) {
        if (v.gate && v.note == note) {
            v.gate = false;
            v.ampEnv.gateOff();
            v.filterEnv.gateOff();
        }
    }
}

void SynthEngine::removeFromStack(int note) {
    int out = 0;
    for (int i = 0; i < stackSize_; ++i) {
        if (noteStack_[i] != note) noteStack_[out++] = noteStack_[i];
    }
    stackSize_ = out;
}

void SynthEngine::monoNoteOn(int note, float velocity) {
    removeFromStack(note);
    if (stackSize_ == kStackSize) {
        std::copy(noteStack_ + 1, noteStack_ + kStackSize, noteStack_);
        --stackSize_;
    }
    noteStack_[stackSize_++] = note;

    Voice &v = voices_[0];
    if (v.gate) {
        // Legato: slide to the new note without restarting the envelopes.
        v.note = note;
        v.targetNote = static_cast<float>(note);
    } else {
        startVoice(v, note, velocity, !v.active());
    }
}

void SynthEngine::monoNoteOff(int note) {
    removeFromStack(note);
    Voice &v = voices_[0];
    if (!v.gate || v.note != note) return;

    if (stackSize_ > 0) {
        const int previous = noteStack_[stackSize_ - 1];
        v.note = previous;
        v.targetNote = static_cast<float>(previous);
    } else {
        v.gate = false;
        v.ampEnv.gateOff();
        v.filterEnv.gateOff();
    }
}

void SynthEngine::releaseAll() {
    for (auto &v : voices_) {
        v.gate = false;
        v.ampEnv.gateOff();
        v.filterEnv.gateOff();
    }
    stackSize_ = 0;
}

// MARK: - Rendering (audio thread)

void SynthEngine::render(float *left, float *right, int frames) {
    const int voiceMode = toIndex(p(SE_VOICE_MODE));
    if (voiceMode != lastVoiceMode_) {
        releaseAll();
        lastVoiceMode_ = voiceMode;
    }

    NoteEvent e;
    while (events_.pop(e)) handle(e);

    const float sr = sampleRate_;
    const float invSr = invSampleRate_;

    // Read every knob once per block.
    const int wave1 = toIndex(p(SE_OSC1_WAVE));
    const int wave2 = toIndex(p(SE_OSC2_WAVE));
    const float osc2Offset = std::round(p(SE_OSC2_SEMI)) + p(SE_OSC2_FINE) / 100.0f;
    const float mix = p(SE_OSC_MIX);
    const float subLevel = p(SE_SUB_LEVEL);
    const float noiseLevel = p(SE_NOISE_LEVEL);

    const float cutoffTarget = p(SE_FILTER_CUTOFF);
    const float k = 4.0f * p(SE_FILTER_RESONANCE);
    const float envAmount = p(SE_FILTER_ENV_AMOUNT);
    const float keytrack = p(SE_FILTER_KEYTRACK);
    const float drive = p(SE_FILTER_DRIVE);
    const float driveGain = 1.0f + drive * 4.0f;
    const float outputGain = (1.0f + k * 0.5f) / (1.0f + drive * 2.0f);
    const float maxCutoff = sr * 0.45f;

    const float log2MinCutoff = std::log2(20.0f);
    const float log2CutoffRange = std::log2(1000.0f);

    for (auto &v : voices_) {
        v.filterEnv.set(mapping::envSeconds(p(SE_FENV_ATTACK)), mapping::envSeconds(p(SE_FENV_DECAY)),
                        p(SE_FENV_SUSTAIN), mapping::envSeconds(p(SE_FENV_RELEASE)), sr);
        v.ampEnv.set(mapping::envSeconds(p(SE_AENV_ATTACK)), mapping::envSeconds(p(SE_AENV_DECAY)),
                     p(SE_AENV_SUSTAIN), mapping::envSeconds(p(SE_AENV_RELEASE)), sr);
    }

    const int lfoWave = toIndex(p(SE_LFO_WAVE));
    const float lfoInc = mapping::lfoHz(p(SE_LFO_RATE)) * invSr;
    const float lfoPitchDepth = p(SE_LFO_TO_PITCH) * p(SE_LFO_TO_PITCH) * 12.0f; // semitones
    const float lfoFilterDepth = p(SE_LFO_TO_FILTER) * 4.0f;                    // octaves

    const float glideSec = voiceMode == 1 ? mapping::glideSeconds(p(SE_GLIDE)) : 0.0f;
    const float glideCoef = glideSec < 0.001f ? 1.0f : smoothingCoef(glideSec * 0.3f, sr);

    const float delayTarget = mapping::delaySeconds(p(SE_DELAY_TIME));
    const float delayFeedback = p(SE_DELAY_FEEDBACK) * 0.95f;
    const float delayMix = p(SE_DELAY_MIX);
    const float volumeTarget = mapping::volumeGain(p(SE_MASTER_VOLUME));

    for (int i = 0; i < frames; ++i) {
        cutoffSmoothed_ += (cutoffTarget - cutoffSmoothed_) * smoothCoef_;
        volumeSmoothed_ += (volumeTarget - volumeSmoothed_) * smoothCoef_;
        delayTimeSmoothed_ += (delayTarget - delayTimeSmoothed_) * delaySmoothCoef_;

        float lfo;
        switch (lfoWave) {
        case 0: lfo = std::sin(kTwoPi * lfoPhase_); break;
        case 1: lfo = 1.0f - 4.0f * std::fabs(lfoPhase_ - 0.5f); break;
        case 2: lfo = lfoPhase_ < 0.5f ? 1.0f : -1.0f; break;
        default: lfo = lfoHold_; break;
        }
        lfoPhase_ += lfoInc;
        if (lfoPhase_ >= 1.0f) {
            lfoPhase_ -= 1.0f;
            lfoHold_ = random_.bipolar();
        }

        const float pitchMod = lfo * lfoPitchDepth;
        const float baseLog2Cutoff = log2MinCutoff + cutoffSmoothed_ * log2CutoffRange + lfo * lfoFilterDepth;

        float sum = 0.0f;
        for (auto &v : voices_) {
            if (!v.active()) continue;

            v.currentNote += (v.targetNote - v.currentNote) * glideCoef;
            const float pitch = v.currentNote + pitchMod;
            const float f1 = midiToHz(pitch);
            const float f2 = midiToHz(pitch + osc2Offset);

            float s = v.osc1.process(wave1, f1 * invSr) * (1.0f - mix) + v.osc2.process(wave2, f2 * invSr) * mix;
            s += v.sub.process(kSquare, f1 * 0.5f * invSr) * subLevel;
            if (noiseLevel > 0.0f) s += random_.bipolar() * noiseLevel;

            const float fEnv = v.filterEnv.process();
            const float aEnv = v.ampEnv.process();

            const float log2Cutoff =
                baseLog2Cutoff + envAmount * fEnv * 7.0f + keytrack * (v.currentNote - 60.0f) / 12.0f;
            const float cutoff = clampf(std::exp2(log2Cutoff), 20.0f, maxCutoff);
            const float g = std::tan(kPi * cutoff * invSr);

            const float y = v.filter.process(s * driveGain, g, k) * outputGain;
            sum += y * aEnv * v.velocityGain;
        }
        sum *= 0.6f;

        float l, r;
        delay_.process(sum, delayTimeSmoothed_ * sr, delayFeedback, delayMix, l, r);
        left[i] = softClip(l * volumeSmoothed_);
        right[i] = softClip(r * volumeSmoothed_);
    }
}

} // namespace synth
