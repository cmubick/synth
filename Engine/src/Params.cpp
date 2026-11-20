#include "SynthEngine.hpp"

#include <cstdio>
#include <cstring>

namespace synth {

namespace {

// In the same order as the se_param enum.
const se_param_info kParams[] = {
    {"osc1.wave", "Wave", 0, 3, 0, 4},
    {"osc2.wave", "Wave", 0, 3, 0, 4},
    {"osc2.semi", "Semi", -24, 24, 0, 49},
    {"osc2.fine", "Fine", -50, 50, 7, 0},
    {"osc.mix", "Osc Mix", 0, 1, 0.5f, 0},
    {"osc.sub", "Sub", 0, 1, 0, 0},
    {"osc.noise", "Noise", 0, 1, 0, 0},

    {"filter.cutoff", "Cutoff", 0, 1, 0.6f, 0},
    {"filter.resonance", "Reso", 0, 1, 0.2f, 0},
    {"filter.env", "Env Amt", -1, 1, 0.3f, 0},
    {"filter.keytrack", "Key Trk", 0, 1, 0.5f, 0},
    {"filter.drive", "Drive", 0, 1, 0.2f, 0},

    {"fenv.attack", "Attack", 0, 1, 0.1f, 0},
    {"fenv.decay", "Decay", 0, 1, 0.6f, 0},
    {"fenv.sustain", "Sustain", 0, 1, 0.4f, 0},
    {"fenv.release", "Release", 0, 1, 0.6f, 0},

    {"aenv.attack", "Attack", 0, 1, 0.05f, 0},
    {"aenv.decay", "Decay", 0, 1, 0.6f, 0},
    {"aenv.sustain", "Sustain", 0, 1, 0.8f, 0},
    {"aenv.release", "Release", 0, 1, 0.55f, 0},

    {"lfo.rate", "Rate", 0, 1, 0.5f, 0},
    {"lfo.wave", "Wave", 0, 3, 0, 4},
    {"lfo.pitch", "Vibrato", 0, 1, 0, 0},
    {"lfo.filter", "Wah", 0, 1, 0, 0},

    {"voice.mode", "Mode", 0, 1, 0, 2},
    {"voice.glide", "Glide", 0, 1, 0, 0},

    {"delay.time", "Time", 0, 1, 0.35f, 0},
    {"delay.feedback", "Repeats", 0, 1, 0.35f, 0},
    {"delay.mix", "Echo", 0, 1, 0, 0},

    {"master.volume", "Volume", 0, 1, 0.7f, 0},
};

static_assert(sizeof(kParams) / sizeof(kParams[0]) == SE_PARAM_COUNT,
              "kParams must have one entry per se_param");

int formatTime(float seconds, char *buf, int size) {
    if (seconds < 1.0f) return std::snprintf(buf, size, "%d ms", static_cast<int>(seconds * 1000.0f + 0.5f));
    return std::snprintf(buf, size, "%.2f s", seconds);
}

int pick(const char *const *names, int count, float v, char *buf, int size) {
    int i = static_cast<int>(v + 0.5f);
    if (i < 0) i = 0;
    if (i >= count) i = count - 1;
    return std::snprintf(buf, size, "%s", names[i]);
}

} // namespace

const se_param_info &paramInfo(se_param p) { return kParams[p]; }

int formatParam(se_param p, float v, char *buf, int size) {
    static const char *const kOscWaves[] = {"Saw", "Square", "Triangle", "Sine"};
    static const char *const kLfoWaves[] = {"Sine", "Triangle", "Square", "Random"};
    static const char *const kModes[] = {"Poly", "Mono"};

    switch (p) {
    case SE_OSC1_WAVE:
    case SE_OSC2_WAVE:
        return pick(kOscWaves, 4, v, buf, size);
    case SE_LFO_WAVE:
        return pick(kLfoWaves, 4, v, buf, size);
    case SE_VOICE_MODE:
        return pick(kModes, 2, v, buf, size);
    case SE_OSC2_SEMI:
        return std::snprintf(buf, size, "%+d st", static_cast<int>(std::round(v)));
    case SE_OSC2_FINE:
        return std::snprintf(buf, size, "%+d ct", static_cast<int>(std::round(v)));
    case SE_FILTER_CUTOFF: {
        const float hz = mapping::cutoffHz(v);
        if (hz >= 1000.0f) return std::snprintf(buf, size, "%.1f kHz", hz / 1000.0f);
        return std::snprintf(buf, size, "%d Hz", static_cast<int>(hz + 0.5f));
    }
    case SE_FILTER_ENV_AMOUNT:
        return std::snprintf(buf, size, "%+d%%", static_cast<int>(std::round(v * 100.0f)));
    case SE_FENV_ATTACK:
    case SE_FENV_DECAY:
    case SE_FENV_RELEASE:
    case SE_AENV_ATTACK:
    case SE_AENV_DECAY:
    case SE_AENV_RELEASE:
        return formatTime(mapping::envSeconds(v), buf, size);
    case SE_GLIDE:
        if (v < 0.02f) return std::snprintf(buf, size, "Off");
        return formatTime(mapping::glideSeconds(v), buf, size);
    case SE_DELAY_TIME:
        return formatTime(mapping::delaySeconds(v), buf, size);
    case SE_LFO_RATE:
        return std::snprintf(buf, size, "%.2f Hz", mapping::lfoHz(v));
    default:
        return std::snprintf(buf, size, "%d%%", static_cast<int>(std::round(v * 100.0f)));
    }
}

int findParam(const char *key) {
    if (!key) return -1;
    for (int i = 0; i < SE_PARAM_COUNT; ++i) {
        if (std::strcmp(kParams[i].key, key) == 0) return i;
    }
    return -1;
}

} // namespace synth
