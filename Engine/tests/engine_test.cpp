// Desktop test for the sound engine: renders a few scenarios through the C
// API, checks the output is sane, measures speed, and writes a WAV you can
// listen to. Build and run with Engine/tests/run_tests.sh.

#include "synth_engine.h"

#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace {

constexpr double kSampleRate = 48000.0;
constexpr int kBlock = 256;
int failures = 0;

void check(bool ok, const char *what) {
    std::printf("  [%s] %s\n", ok ? "ok" : "FAIL", what);
    if (!ok) ++failures;
}

struct Stats {
    float peak = 0;
    double rms = 0;
    bool finite = true;
};

Stats render(se_engine *e, double seconds, std::vector<float> *out = nullptr) {
    Stats s;
    float l[kBlock], r[kBlock];
    const int blocks = static_cast<int>(seconds * kSampleRate / kBlock);
    double sumSq = 0;
    for (int b = 0; b < blocks; ++b) {
        se_render(e, l, r, kBlock);
        for (int i = 0; i < kBlock; ++i) {
            if (!std::isfinite(l[i]) || !std::isfinite(r[i])) s.finite = false;
            s.peak = std::fmax(s.peak, std::fmax(std::fabs(l[i]), std::fabs(r[i])));
            sumSq += l[i] * l[i];
            if (out) {
                out->push_back(l[i]);
                out->push_back(r[i]);
            }
        }
    }
    s.rms = std::sqrt(sumSq / (blocks * kBlock));
    return s;
}

void writeWav(const std::string &path, const std::vector<float> &stereo) {
    FILE *f = std::fopen(path.c_str(), "wb");
    if (!f) return;
    const uint32_t dataBytes = static_cast<uint32_t>(stereo.size() * 2);
    const uint32_t sr = static_cast<uint32_t>(kSampleRate);
    auto u32 = [&](uint32_t v) { std::fwrite(&v, 4, 1, f); };
    auto u16 = [&](uint16_t v) { std::fwrite(&v, 2, 1, f); };
    std::fwrite("RIFF", 1, 4, f);
    u32(36 + dataBytes);
    std::fwrite("WAVEfmt ", 1, 8, f);
    u32(16); u16(1); u16(2); u32(sr); u32(sr * 4); u16(4); u16(16);
    std::fwrite("data", 1, 4, f);
    u32(dataBytes);
    for (float x : stereo) {
        const int16_t v = static_cast<int16_t>(std::lround(std::fmax(-1.0f, std::fmin(1.0f, x)) * 32767.0f));
        std::fwrite(&v, 2, 1, f);
    }
    std::fclose(f);
}

} // namespace

int main(int argc, char **argv) {
    const std::string wavPath = argc > 1 ? argv[1] : "engine_test.wav";
    se_engine *e = se_create(kSampleRate);
    std::vector<float> audio;

    std::printf("Parameters\n");
    {
        bool allOk = true;
        for (int i = 0; i < SE_PARAM_COUNT; ++i) {
            se_param_info info;
            if (se_param_get_info(static_cast<se_param>(i), &info) != 0) allOk = false;
            if (se_param_find(info.key) != i) allOk = false;
            if (info.default_value < info.min_value || info.default_value > info.max_value) allOk = false;
        }
        check(allOk, "every parameter has info, a findable key and an in-range default");
        char buf[32];
        se_param_format(SE_FILTER_CUTOFF, 0.5f, buf, sizeof buf);
        check(std::strcmp(buf, "632 Hz") == 0, (std::string("cutoff 0.5 formats as \"632 Hz\": ") + buf).c_str());
        se_set_param(e, SE_OSC2_SEMI, 99.0f);
        check(se_get_param(e, SE_OSC2_SEMI) == 24.0f, "out-of-range values are clamped");
        se_set_param(e, SE_OSC2_SEMI, 0.0f);
    }

    std::printf("Silence\n");
    {
        Stats s = render(e, 0.2);
        check(s.finite && s.peak == 0.0f, "no notes = silence");
    }

    std::printf("Poly chord\n");
    {
        se_set_param(e, SE_DELAY_MIX, 0.3f);
        se_note_on(e, 48, 0.9f);
        se_note_on(e, 55, 0.8f);
        se_note_on(e, 60, 0.8f);
        se_note_on(e, 64, 0.8f);
        Stats s = render(e, 1.0, &audio);
        check(s.finite, "output is finite");
        check(s.rms > 0.01, "chord is audible");
        check(s.peak <= 1.0f, "output stays within -1..1");
        for (int n : {48, 55, 60, 64}) se_note_off(e, n);
        render(e, 1.0, &audio);
    }

    std::printf("Voice stealing\n");
    {
        for (int n = 40; n < 80; ++n) se_note_on(e, n, 1.0f);
        Stats s = render(e, 0.5);
        check(s.finite && s.peak <= 1.0f, "40 notes at once stay finite and in range");
        se_all_notes_off(e);
        render(e, 3.0);
    }

    std::printf("Mono acid line with screaming resonance\n");
    {
        se_set_param(e, SE_VOICE_MODE, 1);
        se_set_param(e, SE_FILTER_RESONANCE, 1.0f);
        se_set_param(e, SE_FILTER_CUTOFF, 0.25f);
        se_set_param(e, SE_FILTER_ENV_AMOUNT, 0.7f);
        se_set_param(e, SE_FENV_DECAY, 0.55f);
        se_set_param(e, SE_FENV_SUSTAIN, 0.0f);
        se_set_param(e, SE_GLIDE, 0.25f);
        se_set_param(e, SE_FILTER_DRIVE, 1.0f);
        const int line[] = {36, 36, 48, 36, 39, 36, 46, 48};
        Stats worst;
        for (int rep = 0; rep < 2; ++rep) {
            for (int n : line) {
                se_note_on(e, n, 1.0f);
                Stats s = render(e, 0.11, &audio);
                se_note_off(e, n);
                Stats t = render(e, 0.015, &audio);
                worst.finite = worst.finite && s.finite && t.finite;
                worst.peak = std::fmax(worst.peak, std::fmax(s.peak, t.peak));
            }
        }
        check(worst.finite, "self-oscillating filter stays finite");
        check(worst.peak <= 1.0f, "self-oscillating filter stays within -1..1");
        render(e, 0.5, &audio);
    }

    std::printf("Speed\n");
    {
        se_set_param(e, SE_VOICE_MODE, 0);
        render(e, 0.01);
        for (int n = 50; n < 58; ++n) se_note_on(e, n, 1.0f);
        const auto t0 = std::chrono::steady_clock::now();
        render(e, 10.0);
        const auto t1 = std::chrono::steady_clock::now();
        const double ms = std::chrono::duration<double, std::milli>(t1 - t0).count();
        const double realtime = 10000.0 / ms;
        std::printf("  8 voices: 10 s of audio in %.1f ms (%.0fx faster than real time)\n", ms, realtime);
        check(realtime > 20.0, "fast enough with plenty of headroom");
    }

    se_destroy(e);
    writeWav(wavPath, audio);
    std::printf("\nWrote %s\n%s\n", wavPath.c_str(), failures ? "SOME CHECKS FAILED" : "All checks passed");
    return failures ? 1 : 0;
}
