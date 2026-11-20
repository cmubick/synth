#pragma once

#include "synth_engine.h"
#include "dsp/DspMath.hpp"
#include "dsp/Envelope.hpp"
#include "dsp/EventQueue.hpp"
#include "dsp/LadderFilter.hpp"
#include "dsp/Oscillator.hpp"
#include "dsp/StereoDelay.hpp"

#include <array>
#include <atomic>

namespace synth {

const se_param_info &paramInfo(se_param p);

struct NoteEvent {
    enum Type : uint8_t { kNoteOn, kNoteOff, kAllOff };
    Type type = kNoteOn;
    int8_t note = 0;
    float velocity = 0.0f;
};

struct Voice {
    int note = -1;
    bool gate = false;      // key is held
    uint32_t age = 0;       // higher = started more recently
    float velocityGain = 1.0f;
    float currentNote = 60.0f; // pitch in semitones, glides toward targetNote
    float targetNote = 60.0f;

    Oscillator osc1, osc2, sub;
    LadderFilter filter;
    Envelope ampEnv, filterEnv;

    bool active() const { return ampEnv.active(); }
};

class SynthEngine {
public:
    static constexpr int kMaxVoices = 8;

    explicit SynthEngine(double sampleRate);

    void setSampleRate(double sampleRate);

    void noteOn(int note, float velocity);
    void noteOff(int note);
    void allNotesOff();

    void setParam(se_param p, float value);
    float param(se_param p) const;

    void render(float *left, float *right, int frames);

private:
    float p(se_param id) const { return params_[id].load(std::memory_order_relaxed); }

    void handle(const NoteEvent &e);
    void polyNoteOn(int note, float velocity);
    void polyNoteOff(int note);
    void monoNoteOn(int note, float velocity);
    void monoNoteOff(int note);
    void releaseAll();
    void startVoice(Voice &v, int note, float velocity, bool fromSilence);
    void removeFromStack(int note);

    std::array<std::atomic<float>, SE_PARAM_COUNT> params_;
    EventQueue<NoteEvent, 512> events_;
    std::array<Voice, kMaxVoices> voices_;

    float sampleRate_ = 48000.0f;
    float invSampleRate_ = 1.0f / 48000.0f;
    uint32_t voiceCounter_ = 0;
    int lastVoiceMode_ = 0;

    // Mono mode remembers held keys so releasing one falls back to the last.
    static constexpr int kStackSize = 16;
    int noteStack_[kStackSize] = {};
    int stackSize_ = 0;

    float lfoPhase_ = 0.0f;
    float lfoHold_ = 0.0f;
    Random random_;

    float cutoffSmoothed_ = 0.0f;
    float volumeSmoothed_ = 0.0f;
    float delayTimeSmoothed_ = 0.0f;
    float smoothCoef_ = 0.0f;
    float delaySmoothCoef_ = 0.0f;

    StereoDelay delay_;
};

} // namespace synth
