/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.infra

/**
 * Interface for receiving events.
 */
interface KlangMessageReceiver<T> {
    /**
     * Tries to pop an item from the buffer.
     * Returns the item or null if the buffer is empty.
     */
    fun receive(): T?
}
