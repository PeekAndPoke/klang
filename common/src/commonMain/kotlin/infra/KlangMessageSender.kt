/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.infra

/**
 * Interface for dispatching (sending) events.
 */
interface KlangMessageSender<T> {
    /**
     * Tries to push a message into the buffer.
     */
    fun send(msg: T): Boolean

    /**
     * Clears the buffer.
     */
    fun clear()
}
