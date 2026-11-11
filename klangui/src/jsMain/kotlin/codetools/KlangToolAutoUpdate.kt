/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codetools

import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.Unsubscribe

/**
 * Whether a code tool writes each change into the code at once, or waits for its Update button; one switch for all
 * tools, on until someone says otherwise. It holds the value for as long as the page lives and knows nothing of
 * storage: an app that wants it remembered listens to it and gives it what it kept ([set]).
 */
object KlangToolAutoUpdate : Stream<Boolean> {
    private val source = StreamSource(true)

    override fun invoke(): Boolean = source()

    override fun subscribeToStream(sub: (Boolean) -> Unit): Unsubscribe = source.subscribeToStream(sub)

    fun toggle() {
        source(!source())
    }

    /** The switch [on] or off: what the app kept, or what another tab chose. */
    fun set(on: Boolean) {
        if (on != source()) {
            source(on)
        }
    }
}
