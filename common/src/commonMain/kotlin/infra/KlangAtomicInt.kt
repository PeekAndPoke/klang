/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.infra

/**
 * Thread-safe atomic integer using KlangLock for synchronization.
 */
class KlangAtomicInt(initialValue: Int) {
    private val lock = KlangLock()
    private var value: Int = initialValue

    fun get(): Int = lock.withLock { value }

    fun set(newValue: Int) {
        lock.withLock { value = newValue }
    }

    fun incrementAndGet(): Int = lock.withLock {
        value++
        value
    }

    fun decrementAndGet(): Int = lock.withLock {
        value--
        value
    }

    fun getAndIncrement(): Int = lock.withLock {
        val old = value
        value++
        old
    }

    fun getAndDecrement(): Int = lock.withLock {
        val old = value
        value--
        old
    }
}
