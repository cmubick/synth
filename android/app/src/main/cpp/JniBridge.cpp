// The functions Kotlin's NativeEngine calls. Each one is a thin pass-through to
// the engine's C API in Engine/include/synth_engine.h, so Android and iOS drive
// the same engine the same way. One engine for the whole app.

#include "AudioOutput.h"
#include "synth_engine.h"

#include <jni.h>

#include <algorithm>
#include <memory>
#include <string>

namespace {

se_engine *gEngine = nullptr;
std::unique_ptr<AudioOutput> gOutput;

se_engine *engine() {
    if (!gEngine) gEngine = se_create(48000);
    return gEngine;
}

jstring text(JNIEnv *env, const char *s) { return env->NewStringUTF(s ? s : ""); }

se_param param(jint id) { return static_cast<se_param>(id); }
se_drum_param drumParam(jint id) { return static_cast<se_drum_param>(id); }

} // namespace

#define FN(name) JNICALL Java_com_tuesdaygames_synth_engine_NativeEngine_##name

extern "C" {

// MARK: - Lifecycle

JNIEXPORT void FN(create)(JNIEnv *, jobject) { engine(); }

JNIEXPORT jboolean FN(startAudio)(JNIEnv *, jobject) {
    if (!gOutput) gOutput = std::make_unique<AudioOutput>(engine());
    return gOutput->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void FN(stopAudio)(JNIEnv *, jobject) {
    if (gOutput) gOutput->stop();
}

JNIEXPORT jint FN(sampleRate)(JNIEnv *, jobject) { return gOutput ? gOutput->sampleRate() : 0; }

/** [drum tracks, steps, patterns, longest song] from the C header. */
JNIEXPORT jintArray FN(sizes)(JNIEnv *env, jobject) {
    const jint values[] = {SE_DRUM_TRACKS, SE_SEQ_STEPS, SE_SEQ_PATTERNS, SE_SEQ_MAX_CHAIN, SE_PARAM_COUNT,
                           SE_DRUM_PARAM_COUNT};
    jintArray out = env->NewIntArray(6);
    env->SetIntArrayRegion(out, 0, 6, values);
    return out;
}

// MARK: - Synth

JNIEXPORT void FN(noteOn)(JNIEnv *, jobject, jint note, jfloat velocity) { se_note_on(engine(), note, velocity); }
JNIEXPORT void FN(noteOff)(JNIEnv *, jobject, jint note) { se_note_off(engine(), note); }
JNIEXPORT void FN(allNotesOff)(JNIEnv *, jobject) { se_all_notes_off(engine()); }
JNIEXPORT void FN(setParam)(JNIEnv *, jobject, jint id, jfloat value) { se_set_param(engine(), param(id), value); }

JNIEXPORT jstring FN(paramKey)(JNIEnv *env, jobject, jint id) {
    se_param_info info{};
    return text(env, se_param_get_info(param(id), &info) == 0 ? info.key : "");
}

JNIEXPORT jstring FN(paramName)(JNIEnv *env, jobject, jint id) {
    se_param_info info{};
    return text(env, se_param_get_info(param(id), &info) == 0 ? info.name : "");
}

/** [min, max, default, steps]. */
JNIEXPORT jfloatArray FN(paramRange)(JNIEnv *env, jobject, jint id) {
    se_param_info info{};
    se_param_get_info(param(id), &info);
    const jfloat values[] = {info.min_value, info.max_value, info.default_value, static_cast<jfloat>(info.steps)};
    jfloatArray out = env->NewFloatArray(4);
    env->SetFloatArrayRegion(out, 0, 4, values);
    return out;
}

JNIEXPORT jstring FN(paramFormat)(JNIEnv *env, jobject, jint id, jfloat value) {
    char buffer[32] = {0};
    se_param_format(param(id), value, buffer, sizeof buffer);
    return text(env, buffer);
}

// MARK: - Drums

JNIEXPORT jstring FN(drumName)(JNIEnv *env, jobject, jint track) { return text(env, se_drum_name(track)); }
JNIEXPORT jstring FN(drumShortName)(JNIEnv *env, jobject, jint track) { return text(env, se_drum_short_name(track)); }

JNIEXPORT jstring FN(drumParamName)(JNIEnv *env, jobject, jint track, jint p) {
    return text(env, se_drum_param_name(track, drumParam(p)));
}

JNIEXPORT jfloat FN(drumParamDefault)(JNIEnv *, jobject, jint track, jint p) {
    return se_drum_param_default(track, drumParam(p));
}

JNIEXPORT jstring FN(drumParamFormat)(JNIEnv *env, jobject, jint p, jfloat value) {
    char buffer[16] = {0};
    se_drum_param_format(drumParam(p), value, buffer, sizeof buffer);
    return text(env, buffer);
}

JNIEXPORT void FN(drumTrigger)(JNIEnv *, jobject, jint track, jfloat velocity) {
    se_drum_trigger(engine(), track, velocity);
}

JNIEXPORT void FN(drumSetParam)(JNIEnv *, jobject, jint track, jint p, jfloat value) {
    se_drum_set_param(engine(), track, drumParam(p), value);
}

JNIEXPORT void FN(drumSetVolume)(JNIEnv *, jobject, jfloat volume) { se_drum_set_volume(engine(), volume); }

// MARK: - Sequencer

JNIEXPORT void FN(setStep)(JNIEnv *, jobject, jint pattern, jint track, jint step, jint value) {
    se_seq_set_step(engine(), pattern, track, step, value);
}

JNIEXPORT jint FN(getStep)(JNIEnv *, jobject, jint pattern, jint track, jint step) {
    return se_seq_get_step(engine(), pattern, track, step);
}

JNIEXPORT void FN(setLength)(JNIEnv *, jobject, jint pattern, jint steps) {
    se_seq_set_length(engine(), pattern, steps);
}

JNIEXPORT void FN(setTempo)(JNIEnv *, jobject, jfloat bpm) { se_seq_set_tempo(engine(), bpm); }
JNIEXPORT void FN(setSwing)(JNIEnv *, jobject, jfloat amount) { se_seq_set_swing(engine(), amount); }

JNIEXPORT void FN(setChain)(JNIEnv *env, jobject, jintArray patterns) {
    const jsize count = env->GetArrayLength(patterns);
    jint *values = env->GetIntArrayElements(patterns, nullptr);
    se_seq_set_chain(engine(), reinterpret_cast<const int32_t *>(values), count);
    env->ReleaseIntArrayElements(patterns, values, JNI_ABORT);
}

JNIEXPORT void FN(play)(JNIEnv *, jobject) { se_seq_play(engine()); }
JNIEXPORT void FN(stop)(JNIEnv *, jobject) { se_seq_stop(engine()); }

JNIEXPORT void FN(recordHit)(JNIEnv *, jobject, jint track, jboolean accent) {
    se_seq_record_hit(engine(), track, accent ? 1 : 0);
}

/**
 * Everything the screen follows, in one call per frame:
 * [current step, playing pattern, song position, edit count, hit count per drum...].
 */
JNIEXPORT void FN(poll)(JNIEnv *env, jobject, jintArray out) {
    se_engine *e = engine();
    jint values[4 + SE_DRUM_TRACKS];
    values[0] = se_seq_current_step(e);
    values[1] = se_seq_current_pattern(e);
    values[2] = se_seq_chain_position(e);
    values[3] = static_cast<jint>(se_seq_edit_count(e));
    for (int t = 0; t < SE_DRUM_TRACKS; ++t) values[4 + t] = static_cast<jint>(se_drum_hit_count(e, t));
    const jsize n = std::min<jsize>(env->GetArrayLength(out), 4 + SE_DRUM_TRACKS);
    env->SetIntArrayRegion(out, 0, n, values);
}

} // extern "C"
