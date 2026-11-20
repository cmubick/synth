// The C functions declared in synth_engine.h, forwarding to SynthEngine.

#include "SynthEngine.hpp"

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

} // extern "C"
