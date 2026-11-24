// The C functions declared in synth_engine.h, forwarding to SynthEngine.

#include "SynthEngine.hpp"

#include <cmath>
#include <cstdio>

namespace synth {
int formatParam(se_param p, float v, char *buf, int size);
int findParam(const char *key);
} // namespace synth

struct se_engine {
    explicit se_engine(double sampleRate) : engine(sampleRate) {}
    synth::SynthEngine engine;
};

extern "C" {

se_engine *se_create(double sample_rate) { return new se_engine(sample_rate); }

void se_destroy(se_engine *e) { delete e; }

void se_set_sample_rate(se_engine *e, double sample_rate) {
    if (e) e->engine.setSampleRate(sample_rate);
}

void se_note_on(se_engine *e, int32_t note, float velocity) {
    if (e) e->engine.noteOn(note, velocity);
}

void se_note_off(se_engine *e, int32_t note) {
    if (e) e->engine.noteOff(note);
}

void se_all_notes_off(se_engine *e) {
    if (e) e->engine.allNotesOff();
}

void se_set_param(se_engine *e, se_param param, float value) {
    if (e) e->engine.setParam(param, value);
}

float se_get_param(const se_engine *e, se_param param) { return e ? e->engine.param(param) : 0.0f; }

void se_render(se_engine *e, float *left, float *right, int32_t frames) {
    if (!e || !left || !right || frames <= 0) return;
    e->engine.render(left, right, frames);
}

int32_t se_param_get_info(se_param param, se_param_info *out) {
    if (!out || param < 0 || param >= SE_PARAM_COUNT) return -1;
    *out = synth::paramInfo(param);
    return 0;
}

int32_t se_param_format(se_param param, float value, char *buffer, int32_t buffer_size) {
    if (!buffer || buffer_size <= 0 || param < 0 || param >= SE_PARAM_COUNT) return -1;
    return synth::formatParam(param, value, buffer, buffer_size);
}

int32_t se_param_find(const char *key) { return synth::findParam(key); }

// MARK: - Drums

const char *se_drum_name(int32_t track) { return synth::drumName(track); }
const char *se_drum_short_name(int32_t track) { return synth::drumShortName(track); }
const char *se_drum_param_name(int32_t track, se_drum_param param) { return synth::drumParamName(track, param); }
float se_drum_param_default(int32_t track, se_drum_param param) { return synth::drumParamDefault(track, param); }

int32_t se_drum_param_format(se_drum_param param, float value, char *buffer, int32_t buffer_size) {
    if (!buffer || buffer_size <= 0) return -1;
    if (param == SE_DRUM_TUNE) {
        const int semitones = static_cast<int>(std::round((value - 0.5f) * 24.0f));
        return std::snprintf(buffer, buffer_size, "%+d st", semitones);
    }
    return std::snprintf(buffer, buffer_size, "%d%%", static_cast<int>(std::round(value * 100.0f)));
}

void se_drum_trigger(se_engine *e, int32_t track, float velocity) {
    if (e) e->engine.drumHit(track, velocity);
}

void se_drum_set_param(se_engine *e, int32_t track, se_drum_param param, float value) {
    if (e) e->engine.drums().setParam(track, param, value);
}

float se_drum_get_param(const se_engine *e, int32_t track, se_drum_param param) {
    return e ? e->engine.drums().param(track, param) : 0.0f;
}

void se_drum_set_volume(se_engine *e, float volume) {
    if (e) e->engine.drums().setVolume(volume);
}

float se_drum_get_volume(const se_engine *e) { return e ? e->engine.drums().volume() : 0.0f; }

uint32_t se_drum_hit_count(const se_engine *e, int32_t track) {
    return e ? e->engine.drums().hitCount(track) : 0;
}

// MARK: - Sequencer

void se_seq_set_step(se_engine *e, int32_t pattern, int32_t track, int32_t step, int32_t value) {
    if (e) e->engine.drums().setStep(pattern, track, step, value);
}

int32_t se_seq_get_step(const se_engine *e, int32_t pattern, int32_t track, int32_t step) {
    return e ? e->engine.drums().step(pattern, track, step) : 0;
}

void se_seq_set_length(se_engine *e, int32_t pattern, int32_t steps) {
    if (e) e->engine.drums().setLength(pattern, steps);
}

int32_t se_seq_get_length(const se_engine *e, int32_t pattern) {
    return e ? e->engine.drums().length(pattern) : SE_SEQ_STEPS;
}

void se_seq_set_tempo(se_engine *e, float bpm) {
    if (e) e->engine.drums().setTempo(bpm);
}

float se_seq_get_tempo(const se_engine *e) { return e ? e->engine.drums().tempo() : 120.0f; }

void se_seq_set_swing(se_engine *e, float amount) {
    if (e) e->engine.drums().setSwing(amount);
}

float se_seq_get_swing(const se_engine *e) { return e ? e->engine.drums().swing() : 0.0f; }

void se_seq_set_chain(se_engine *e, const int32_t *patterns, int32_t count) {
    if (e) e->engine.drums().setChain(patterns, count);
}

void se_seq_play(se_engine *e) {
    if (e) e->engine.drums().play();
}

void se_seq_stop(se_engine *e) {
    if (e) e->engine.drums().stop();
}

int32_t se_seq_is_playing(const se_engine *e) { return e && e->engine.drums().isPlaying() ? 1 : 0; }
int32_t se_seq_current_step(const se_engine *e) { return e ? e->engine.drums().currentStep() : -1; }
int32_t se_seq_current_pattern(const se_engine *e) { return e ? e->engine.drums().currentPattern() : 0; }
int32_t se_seq_chain_position(const se_engine *e) { return e ? e->engine.drums().chainPosition() : 0; }
uint32_t se_seq_edit_count(const se_engine *e) { return e ? e->engine.drums().editCount() : 0; }

void se_seq_record_hit(se_engine *e, int32_t track, int32_t accent) {
    if (e) e->engine.drumRecord(track, accent != 0);
}

} // extern "C"
