#pragma once

#include <array>
#include <atomic>
#include <cstddef>

namespace synth {

/*
 * Fixed-size queue that carries note events from the UI or MIDI threads to
 * the audio thread. The audio thread (single consumer) never waits; the
 * senders take a tiny spin lock among themselves so several threads can
 * send safely.
 */
template <typename T, size_t N>
class EventQueue {
public:
    bool push(const T &item) {
        while (writerLock_.test_and_set(std::memory_order_acquire)) {
        }
        const size_t tail = tail_.load(std::memory_order_relaxed);
        const size_t next = (tail + 1) % N;
        bool ok = false;
        if (next != head_.load(std::memory_order_acquire)) {
            buffer_[tail] = item;
            tail_.store(next, std::memory_order_release);
            ok = true;
        }
        writerLock_.clear(std::memory_order_release);
        return ok;
    }

    bool pop(T &item) {
        const size_t head = head_.load(std::memory_order_relaxed);
        if (head == tail_.load(std::memory_order_acquire)) return false;
        item = buffer_[head];
        head_.store((head + 1) % N, std::memory_order_release);
        return true;
    }

private:
    std::array<T, N> buffer_{};
    std::atomic<size_t> head_{0};
    std::atomic<size_t> tail_{0};
    std::atomic_flag writerLock_ = ATOMIC_FLAG_INIT;
};

} // namespace synth
