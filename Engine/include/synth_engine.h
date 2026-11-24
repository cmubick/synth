/*
 * synth_engine.h — the public C API of the shared sound engine.
 *
 * This header is the only thing the platform apps see. iOS calls it from
 * Swift (via the bridging header); Android will call it through JNI.
 * Keeping it plain C means both platforms share one engine untouched.
 *
 * Threading:
 *   - se_render() runs on the real-time audio thread. It never allocates,
 *     locks, or blocks.
 *   - se_note_on / se_note_off / se_all_notes_off / se_set_param may be
 *     called from any other thread (UI, MIDI). They are lock-free for the
 *     audio thread.
 *   - se_create / se_destroy / se_set_sample_rate allocate memory and must
 *     NOT be called while se_render() is running.
 */
#ifndef SYNTH_ENGINE_H
#define SYNTH_ENGINE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct se_engine se_engine;

/*
 * Every sound parameter. Values are plain floats in the range given by
 * se_param_info (e.g. OSC2_SEMI is -24..24 semitones, waveforms are an
 * index 0..3, most knobs are 0..1). The engine maps them to real units.
 *
 * Append new parameters before SE_PARAM_COUNT. Saved sounds refer to
 * parameters by their stable string key, not by this number.
 */
typedef enum se_param : int32_t {
    SE_OSC1_WAVE = 0,       /* 0 saw, 1 square, 2 triangle, 3 sine */
    SE_OSC2_WAVE,           /* same as OSC1 */
    SE_OSC2_SEMI,           /* -24..24 semitones */
    SE_OSC2_FINE,           /* -50..50 cents */
    SE_OSC_MIX,             /* 0 = all osc 1, 1 = all osc 2 */
    SE_SUB_LEVEL,           /* square one octave below osc 1 */
    SE_NOISE_LEVEL,

    SE_FILTER_CUTOFF,       /* 0..1, ~20 Hz .. 20 kHz */
    SE_FILTER_RESONANCE,    /* 0..1, self-oscillates near 1 */
    SE_FILTER_ENV_AMOUNT,   /* -1..1 */
    SE_FILTER_KEYTRACK,     /* 0..1 */
    SE_FILTER_DRIVE,        /* 0..1 */

    SE_FENV_ATTACK,         /* filter envelope, times 0..1 (~1 ms .. 5 s) */
    SE_FENV_DECAY,
    SE_FENV_SUSTAIN,
    SE_FENV_RELEASE,

    SE_AENV_ATTACK,         /* amp envelope */
    SE_AENV_DECAY,
    SE_AENV_SUSTAIN,
    SE_AENV_RELEASE,

    SE_LFO_RATE,            /* 0..1, ~0.05 Hz .. 20 Hz */
    SE_LFO_WAVE,            /* 0 sine, 1 triangle, 2 square, 3 sample & hold */
    SE_LFO_TO_PITCH,        /* vibrato depth */
    SE_LFO_TO_FILTER,       /* wah / wobble depth */

    SE_VOICE_MODE,          /* 0 poly, 1 mono (legato) */
    SE_GLIDE,               /* portamento time, mono mode */

    SE_DELAY_TIME,
    SE_DELAY_FEEDBACK,
    SE_DELAY_MIX,

    SE_MASTER_VOLUME,

    SE_PARAM_COUNT
} se_param;

typedef struct se_param_info {
    const char *key;        /* stable id for saving, e.g. "filter.cutoff" */
    const char *name;       /* short display name, e.g. "Cutoff" */
    float min_value;
    float max_value;
    float default_value;
    int32_t steps;          /* 0 = continuous, else number of positions */
} se_param_info;

/* Lifecycle (not real-time safe). */
se_engine *se_create(double sample_rate);
void se_destroy(se_engine *engine);
void se_set_sample_rate(se_engine *engine, double sample_rate);

/* Playing. note is a MIDI note number (60 = middle C), velocity 0..1. */
void se_note_on(se_engine *engine, int32_t note, float velocity);
void se_note_off(se_engine *engine, int32_t note);
void se_all_notes_off(se_engine *engine);

/* Sound parameters. Values are clamped to the parameter's range. */
void se_set_param(se_engine *engine, se_param param, float value);
float se_get_param(const se_engine *engine, se_param param);

/* Audio thread: render `frames` samples into two non-interleaved buffers. */
void se_render(se_engine *engine, float *left, float *right, int32_t frames);

/* Parameter metadata, shared by every platform's UI. */
int32_t se_param_get_info(se_param param, se_param_info *out); /* 0 = ok */
int32_t se_param_format(se_param param, float value, char *buffer, int32_t buffer_size);
int32_t se_param_find(const char *key); /* index, or -1 if unknown */

/* ------------------------------------------------------------------------
 * Drum machine: an 808-style kit plus a step sequencer.
 *
 * Tracks (in order): kick, snare, clap, low tom, high tom, rim, cowbell,
 * closed hat, open hat, cymbal. A pattern is 16 steps per track; each
 * step is 0 (off), 1 (on) or 2 (accent). Patterns play in a "chain":
 * one pattern looping, or a list of patterns forming a song. A new chain
 * takes effect at the end of the current bar, so switching patterns
 * while playing stays on the beat.
 * ---------------------------------------------------------------------- */

#define SE_DRUM_TRACKS 10
#define SE_SEQ_STEPS 16
#define SE_SEQ_PATTERNS 8
#define SE_SEQ_MAX_CHAIN 64

typedef enum se_drum_param : int32_t {
    SE_DRUM_LEVEL = 0,  /* 0..1 */
    SE_DRUM_TUNE,       /* 0..1, 0.5 = classic pitch, +/- one octave */
    SE_DRUM_DECAY,      /* 0..1, 0.5 = classic length */
    SE_DRUM_TONE,       /* 0..1, a color control that differs per drum */
    SE_DRUM_PARAM_COUNT
} se_drum_param;

const char *se_drum_name(int32_t track);        /* "Kick" */
const char *se_drum_short_name(int32_t track);  /* "BD" */
const char *se_drum_param_name(int32_t track, se_drum_param param); /* "Tone" may be "Click", "Snap"... */
float se_drum_param_default(int32_t track, se_drum_param param);
int32_t se_drum_param_format(se_drum_param param, float value, char *buffer, int32_t buffer_size);

void se_drum_trigger(se_engine *engine, int32_t track, float velocity);
void se_drum_set_param(se_engine *engine, int32_t track, se_drum_param param, float value);
float se_drum_get_param(const se_engine *engine, int32_t track, se_drum_param param);
void se_drum_set_volume(se_engine *engine, float volume); /* 0..1, the whole kit */
float se_drum_get_volume(const se_engine *engine);
/* Goes up by one every time the track plays; the UI watches it to flash pads. */
uint32_t se_drum_hit_count(const se_engine *engine, int32_t track);

void se_seq_set_step(se_engine *engine, int32_t pattern, int32_t track, int32_t step, int32_t value);
int32_t se_seq_get_step(const se_engine *engine, int32_t pattern, int32_t track, int32_t step);
void se_seq_set_length(se_engine *engine, int32_t pattern, int32_t steps); /* 1..16 */
int32_t se_seq_get_length(const se_engine *engine, int32_t pattern);
void se_seq_set_tempo(se_engine *engine, float bpm); /* 40..240 */
float se_seq_get_tempo(const se_engine *engine);
void se_seq_set_swing(se_engine *engine, float amount); /* 0 straight .. 1 heavy shuffle */
float se_seq_get_swing(const se_engine *engine);
void se_seq_set_chain(se_engine *engine, const int32_t *patterns, int32_t count);

void se_seq_play(se_engine *engine);
void se_seq_stop(se_engine *engine);
int32_t se_seq_is_playing(const se_engine *engine);
int32_t se_seq_current_step(const se_engine *engine);    /* -1 when stopped */
int32_t se_seq_current_pattern(const se_engine *engine);
int32_t se_seq_chain_position(const se_engine *engine);
/* Goes up by one whenever the engine itself changes steps (live recording). */
uint32_t se_seq_edit_count(const se_engine *engine);

/* Play a drum now and, if the sequencer is running, write it into the
 * playing pattern at the nearest step. */
void se_seq_record_hit(se_engine *engine, int32_t track, int32_t accent);

#ifdef __cplusplus
}
#endif

#endif /* SYNTH_ENGINE_H */
