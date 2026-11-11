/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.infra

/**
 * A simple ring buffer to pass events between threads (e.g., Main Thread to Audio Thread).
 * Uses a fixed capacity and a lock for thread safety.
 */
class KlangRingBuffer<T>(private val capacity: Int) : KlangMessageSender<T>, KlangMessageReceiver<T> {
    private val buffer = arrayOfNulls<Any?>(capacity)
    private var head = 0 // Write index
    private var tail = 0 // Read index
    private val lock = KlangLock()

    /**
     * The sends refused because the ring was full, since it was made: each such message was dropped (KLANG-00431 D10,
     * audit L1-11). Monotone, [clear] leaves it; a Double, as the diagnostics carry it (no Long in the audio paths).
     */
    var refusedSends: Double = 0.0
        private set

    override fun send(msg: T): Boolean = lock.withLock {
        val nextHead = (head + 1) % capacity
        if (nextHead == tail) {
            refusedSends += 1.0

            return@withLock false // Buffer full
        }
        buffer[head] = msg
        head = nextHead
        return@withLock true
    }

    override fun receive(): T? = lock.withLock {
        if (head == tail) {
            return@withLock null // Buffer empty
        }
        @Suppress("UNCHECKED_CAST")
        val item = buffer[tail] as T

        buffer[tail] = null // Help GC
        tail = (tail + 1) % capacity

        return@withLock item
    }

    override fun clear() = lock.withLock {
        while (head != tail) {
            buffer[tail] = null
            tail = (tail + 1) % capacity
        }
        head = 0
        tail = 0
    }
}
