#include "AudioOutput.h"

#include <android/log.h>

#include <algorithm>
#include <thread>

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "808Synth", __VA_ARGS__)

bool AudioOutput::start() {
    std::lock_guard<std::mutex> guard(lock_);
    wanted_ = true;
    if (stream_) return true;
    return openLocked();
}

void AudioOutput::stop() {
    std::lock_guard<std::mutex> guard(lock_);
    wanted_ = false;
    closeLocked();
}

bool AudioOutput::openLocked() {
    AAudioStreamBuilder *builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) return false;

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    // Low latency so keys and pads feel instant. Shared mode works on every phone
    // and on the emulator; the system still gives the fast path where it can.
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    if (__builtin_available(android 28, *)) {
        AAudioStreamBuilder_setUsage(builder, AAUDIO_USAGE_MEDIA);
        AAudioStreamBuilder_setContentType(builder, AAUDIO_CONTENT_TYPE_MUSIC);
    }
    AAudioStreamBuilder_setDataCallback(builder, &AudioOutput::onAudio, this);
    AAudioStreamBuilder_setErrorCallback(builder, &AudioOutput::onError, this);

    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &stream_);
    AAudioStreamBuilder_delete(builder);
    if (result != AAUDIO_OK || !stream_) {
        LOG("could not open audio: %s", AAudio_convertResultToText(result));
        stream_ = nullptr;
        return false;
    }

    // The engine renders at whatever rate the phone's output runs at. This happens
    // before the stream starts, so it never overlaps a render.
    const int32_t rate = AAudioStream_getSampleRate(stream_);
    sampleRate_ = rate;
    se_set_sample_rate(engine_, rate);

    // Two bursts of buffering: as low as is safe.
    const int32_t burst = AAudioStream_getFramesPerBurst(stream_);
    framesPerBurst_ = burst;
    AAudioStream_setBufferSizeInFrames(stream_, burst * 2);

    const int32_t capacity = std::max(AAudioStream_getBufferCapacityInFrames(stream_), 4096);
    left_.assign(capacity, 0.0f);
    right_.assign(capacity, 0.0f);

    result = AAudioStream_requestStart(stream_);
    if (result != AAUDIO_OK) {
        LOG("could not start audio: %s", AAudio_convertResultToText(result));
        closeLocked();
        return false;
    }
    LOG("audio running: %d Hz, burst %d frames", rate, burst);
    return true;
}

void AudioOutput::closeLocked() {
    if (!stream_) return;
    AAudioStream_requestStop(stream_);
    AAudioStream_close(stream_);
    stream_ = nullptr;
}

// Real-time audio thread: no allocation, no locks, just render.
aaudio_data_callback_result_t AudioOutput::onAudio(AAudioStream *, void *user, void *data, int32_t frames) {
    auto *self = static_cast<AudioOutput *>(user);
    auto *out = static_cast<float *>(data);
    const int32_t chunk = static_cast<int32_t>(self->left_.size());
    int32_t done = 0;
    while (done < frames) {
        const int32_t n = std::min(chunk, frames - done);
        se_render(self->engine_, self->left_.data(), self->right_.data(), n);
        for (int32_t i = 0; i < n; ++i) {
            out[(done + i) * 2] = self->left_[i];
            out[(done + i) * 2 + 1] = self->right_[i];
        }
        done += n;
    }
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

// Called when the output goes away (headphones unplugged, Bluetooth dropped...).
// A stream can't be closed from its own callback, so reopen on another thread.
void AudioOutput::onError(AAudioStream *, void *user, aaudio_result_t error) {
    auto *self = static_cast<AudioOutput *>(user);
    LOG("audio error: %s", AAudio_convertResultToText(error));
    if (error == AAUDIO_ERROR_DISCONNECTED) {
        std::thread([self] { self->restartAfterDisconnect(); }).detach();
    }
}

void AudioOutput::restartAfterDisconnect() {
    std::lock_guard<std::mutex> guard(lock_);
    closeLocked();
    if (wanted_) openLocked();
}
